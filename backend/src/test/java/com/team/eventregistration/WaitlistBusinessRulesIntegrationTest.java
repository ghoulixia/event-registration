package com.team.eventregistration;

import com.team.eventregistration.entity.*;
import com.team.eventregistration.repository.*;
import com.team.eventregistration.security.JwtService;
import com.team.eventregistration.service.SlotOfferService;
import com.team.eventregistration.service.WaitlistService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class WaitlistBusinessRulesIntegrationTest extends PostgresTestSupport {
    @Autowired private SlotOfferService offers;
    @Autowired private WaitlistService waitlists;
    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private WaitlistEntryRepository waitlistEntryRepository;
    @Autowired private SlotOfferRepository slotOfferRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;
    @Autowired private JwtService jwt;
    @Autowired private WebApplicationContext context;

    private final List<Long> eventIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private MockMvc mvc;

    @BeforeEach
    void setUpHttp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void cleanUp() {
        for (Long eventId : eventIds) {
            jdbc.update("DELETE FROM audit_logs WHERE target_type = 'EVENT' AND target_id = ?", eventId);
            slotOfferRepository.deleteAll(slotOfferRepository.findByEvent_Id(eventId));
            registrationRepository.findByEvent_Id(eventId)
                    .forEach(registration -> registrationRepository.deleteById(registration.getId()));
            waitlistEntryRepository.findByEvent_Id(eventId)
                    .forEach(entry -> waitlistEntryRepository.deleteById(entry.getId()));
            eventRepository.deleteById(eventId);
        }
        for (Long userId : userIds) {
            userRepository.deleteById(userId);
        }
    }

    @Test
    void removedParticipantCannotJoinAFullEventsWaitlist() throws Exception {
        User removedUser = user("removed-join");
        User confirmedUser = user("confirmed");
        Event event = event(1);
        Registration removed = removedRegistration(removedUser, event);
        registrationRepository.saveAndFlush(new Registration(confirmedUser, event, now()));
        event.setRegisteredCount(1);
        eventRepository.saveAndFlush(event);

        mvc.perform(post("/api/events/{id}/waitlist", event.getId())
                        .header("Authorization", token(removedUser)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("message", containsString("Removed participants")));

        assertTrue(waitlistEntryRepository.findByEvent_Id(event.getId()).isEmpty());
        assertEquals(RegistrationStatus.REMOVED,
                registrationRepository.findById(removed.getId()).orElseThrow().getStatus());
        assertThrows(IllegalStateException.class, () -> removed.confirmAgain(now()));
        assertCounters(event.getId(), 1, 0);
        assertEquals(0, confirmationAuditCount(event.getId()));
    }

    @Test
    void allocationCancelsLegacyRemovedWaitingEntryAndOffersTheNextEligibleUser() {
        User removedUser = user("removed-waiting");
        Event event = event(1);
        removedRegistration(removedUser, event);
        WaitlistEntry removedEntry = entry(removedUser, event);
        WaitlistEntry nextEntry = entry(user("eligible-first"), event);
        WaitlistEntry laterEntry = entry(user("eligible-second"), event);

        offers.allocateAvailableSlotsToWaitlist(event.getId());

        assertEntryStatus(removedEntry, WaitlistEntryStatus.CANCELLED);
        assertEntryStatus(nextEntry, WaitlistEntryStatus.OFFERED);
        assertEntryStatus(laterEntry, WaitlistEntryStatus.WAITING);
        assertTrue(slotOfferRepository.findByUser_IdAndEvent_IdAndStatus(
                removedUser.getId(), event.getId(), SlotOfferStatus.PENDING).isEmpty());
        assertEquals(1, slotOfferRepository.findByEvent_Id(event.getId()).size());
        assertCounters(event.getId(), 0, 1);
    }

    @Test
    void removedParticipantAcceptReturnsConflictAfterCommittingRevocationAndFifoHandover() throws Exception {
        User removedUser = user("removed-accept");
        User nextUser = user("next-accept");
        User otherUser = user("not-offer-owner");
        Event event = event(1);
        WaitlistEntry removedEntry = entry(removedUser, event);
        SlotOffer removedOffer = offers.offerNextWaitingEntry(event.getId()).orElseThrow();
        Registration removed = removedRegistration(removedUser, event);
        WaitlistEntry nextEntry = entry(nextUser, event);

        mvc.perform(post("/api/offers/{id}/accept", removedOffer.getId())
                        .header("Authorization", token(otherUser)))
                .andExpect(status().isNotFound());
        assertEquals(SlotOfferStatus.PENDING, savedOffer(removedOffer).getStatus());
        assertCounters(event.getId(), 0, 1);

        mvc.perform(post("/api/offers/{id}/accept", removedOffer.getId())
                        .header("Authorization", token(removedUser)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("message", containsString("REVOKED")));

        SlotOffer revoked = savedOffer(removedOffer);
        assertEquals(SlotOfferStatus.REVOKED, revoked.getStatus());
        assertNotNull(revoked.getResolvedAt());
        assertEntryStatus(removedEntry, WaitlistEntryStatus.CANCELLED);
        assertEntryStatus(nextEntry, WaitlistEntryStatus.OFFERED);
        Registration savedRemoved = registrationRepository.findById(removed.getId()).orElseThrow();
        assertEquals(RegistrationStatus.REMOVED, savedRemoved.getStatus());
        assertEquals("Removal must remain effective", savedRemoved.getRemovedReason());
        assertEquals(removed.getRemovedByUserId(), savedRemoved.getRemovedByUserId());
        assertCounters(event.getId(), 0, 1);
        assertEquals(0, confirmationAuditCount(event.getId()));

        SlotOffer nextOffer = slotOfferRepository.findByWaitlistEntry_IdAndStatus(
                nextEntry.getId(), SlotOfferStatus.PENDING).orElseThrow();
        mvc.perform(post("/api/offers/{id}/accept", nextOffer.getId())
                        .header("Authorization", token(nextUser)))
                .andExpect(status().isNoContent());
        assertCounters(event.getId(), 1, 0);
        assertEquals(1, confirmationAuditCount(event.getId()));
        assertEquals(RegistrationStatus.REMOVED,
                registrationRepository.findById(removed.getId()).orElseThrow().getStatus());
    }

    @Test
    void allocationRevokesLegacyRemovedPendingOfferEvenWhenAllCapacityIsHeld() {
        User removedUser = user("removed-pending");
        Event event = event(1);
        WaitlistEntry removedEntry = entry(removedUser, event);
        SlotOffer removedOffer = offers.offerNextWaitingEntry(event.getId()).orElseThrow();
        removedRegistration(removedUser, event);
        WaitlistEntry nextEntry = entry(user("next-after-revocation"), event);

        offers.allocateAvailableSlotsToWaitlist(event.getId());

        assertEquals(SlotOfferStatus.REVOKED, savedOffer(removedOffer).getStatus());
        assertEntryStatus(removedEntry, WaitlistEntryStatus.CANCELLED);
        assertEntryStatus(nextEntry, WaitlistEntryStatus.OFFERED);
        assertCounters(event.getId(), 0, 1);
        assertEquals(0, confirmationAuditCount(event.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "scheduler", "allocation", "offer-next", "accept-expired", "decline-expired",
            "decline-current", "withdraw-expired", "withdraw-current"
    })
    void everyReleaseTriggerRefillsAllFreedSlotsInFifoOrder(String trigger) throws Exception {
        Event event = event(2);
        User firstUser = user("first-held");
        entry(firstUser, event);
        entry(user("second-held"), event);
        SlotOffer firstOffer = offers.offerNextWaitingEntry(event.getId()).orElseThrow();
        SlotOffer secondOffer = offers.offerNextWaitingEntry(event.getId()).orElseThrow();
        WaitlistEntry firstWaiting = entry(user("first-waiting"), event);
        WaitlistEntry secondWaiting = entry(user("second-waiting"), event);
        WaitlistEntry thirdWaiting = entry(user("third-waiting"), event);
        jdbc.update("UPDATE waitlist_entries SET joined_at = ? WHERE event_id = ?", now(), event.getId());
        expireInDatabase(secondOffer);
        boolean firstRemainsCurrent = trigger.endsWith("-current");
        if (!firstRemainsCurrent) {
            expireInDatabase(firstOffer);
        }

        switch (trigger) {
            case "scheduler" -> offers.expireOffers(now());
            case "allocation" -> offers.allocateAvailableSlotsToWaitlist(event.getId());
            case "offer-next" -> assertEquals(firstWaiting.getId(),
                    offers.offerNextWaitingEntry(event.getId()).orElseThrow().getWaitlistEntry().getId());
            case "accept-expired" -> mvc.perform(post("/api/offers/{id}/accept", firstOffer.getId())
                            .header("Authorization", token(firstUser)))
                    .andExpect(status().isConflict());
            case "decline-expired", "decline-current" -> mvc.perform(
                            post("/api/offers/{id}/decline", firstOffer.getId())
                                    .header("Authorization", token(firstUser)))
                    .andExpect(firstRemainsCurrent ? status().isNoContent() : status().isConflict());
            case "withdraw-expired", "withdraw-current" ->
                    waitlists.leaveWaitlist(firstUser.getId(), event.getId());
            default -> fail("Unexpected release trigger: " + trigger);
        }

        assertEquals(firstRemainsCurrent ? SlotOfferStatus.DECLINED : SlotOfferStatus.EXPIRED,
                savedOffer(firstOffer).getStatus());
        assertEquals(SlotOfferStatus.EXPIRED, savedOffer(secondOffer).getStatus());
        assertNotNull(savedOffer(firstOffer).getResolvedAt());
        assertNotNull(savedOffer(secondOffer).getResolvedAt());
        assertEntryStatus(firstWaiting, WaitlistEntryStatus.OFFERED);
        assertEntryStatus(secondWaiting, WaitlistEntryStatus.OFFERED);
        assertEntryStatus(thirdWaiting, WaitlistEntryStatus.WAITING);
        assertEquals(2, slotOfferRepository.findByEvent_IdAndStatus(
                event.getId(), SlotOfferStatus.PENDING).size());
        assertEquals(4, slotOfferRepository.findByEvent_Id(event.getId()).size());
        assertCounters(event.getId(), 0, 2);
        assertTrue(registrationRepository.findByEvent_Id(event.getId()).isEmpty());
        assertEquals(0, confirmationAuditCount(event.getId()));
    }

    private User user(String suffix) {
        User user = userRepository.saveAndFlush(new User(
                "waitlist-rule-" + suffix + "-" + UUID.randomUUID() + "@test.com",
                "Waitlist rules " + suffix, "hash", null, Role.USER));
        userIds.add(user.getId());
        return user;
    }

    private Event event(int capacity) {
        Event event = new Event();
        event.setTitle("Waitlist business rules event");
        event.setOrganizerName("Test Organizer");
        event.setCity(City.HANOI);
        event.setCategory(Category.TECHNOLOGY);
        event.setStartTime(now().plusHours(2));
        event.setEndTime(event.getStartTime().plusHours(1));
        event.setCapacity(capacity);
        event.setRegisteredCount(0);
        event.setHeldCount(0);
        event.setStatus(EventStatus.OPEN);
        Event saved = eventRepository.saveAndFlush(event);
        eventIds.add(saved.getId());
        return saved;
    }

    private Registration removedRegistration(User user, Event event) {
        Registration registration = new Registration(user, event, now().minusMinutes(5));
        registration.remove(now().minusMinutes(1), user.getId(), "Removal must remain effective");
        return registrationRepository.saveAndFlush(registration);
    }

    private WaitlistEntry entry(User user, Event event) {
        return waitlistEntryRepository.saveAndFlush(new WaitlistEntry(user, event));
    }

    private void expireInDatabase(SlotOffer offer) {
        jdbc.update("UPDATE slot_offers SET created_at = ?, expires_at = ? WHERE id = ?",
                now().minusMinutes(3), now().minusMinutes(2), offer.getId());
    }

    private SlotOffer savedOffer(SlotOffer offer) {
        return slotOfferRepository.findById(offer.getId()).orElseThrow();
    }

    private void assertEntryStatus(WaitlistEntry entry, WaitlistEntryStatus expected) {
        assertEquals(expected, waitlistEntryRepository.findById(entry.getId()).orElseThrow().getStatus());
    }

    private void assertCounters(Long eventId, int registered, int held) {
        Event saved = eventRepository.findById(eventId).orElseThrow();
        assertEquals(registered, saved.getRegisteredCount());
        assertEquals(held, saved.getHeldCount());
        assertTrue(saved.getRegisteredCount() >= 0);
        assertTrue(saved.getHeldCount() >= 0);
        assertTrue(saved.getRegisteredCount() + saved.getHeldCount() <= saved.getCapacity());
    }

    private int confirmationAuditCount(Long eventId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE target_type = 'EVENT' AND target_id = ? AND action = 'REGISTRATION_CONFIRM'",
                Integer.class, eventId);
    }

    private String token(User user) {
        return "Bearer " + jwt.issue(user.getId());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}

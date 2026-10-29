package com.team.eventregistration.service;

import com.team.eventregistration.PostgresTestSupport;
import com.team.eventregistration.entity.*;
import com.team.eventregistration.exception.EventFullException;
import com.team.eventregistration.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostgreSQL acceptance coverage for the cancellation-to-offer flow
 * and the FIFO/TTL state transitions required by the SRS.
 */
@SpringBootTest
class WaitlistAcceptanceIntegrationTest extends PostgresTestSupport {
    @Autowired private RegistrationService registrations;
    @Autowired private EventService events;
    @Autowired private SlotOfferService offers;
    @Autowired private WaitlistService waitlists;
    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private WaitlistEntryRepository waitlistEntryRepository;
    @Autowired private SlotOfferRepository slotOfferRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private Clock clock;

    private final List<Long> eventIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

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
            auditLogRepository.deleteByActorUserId(userId);
            userRepository.deleteById(userId);
        }
        eventIds.clear();
        userIds.clear();
    }

    @Test
    void cancellingConfirmedRegistrationOffersTheFreedSlotToTheFirstWaitingUser() {
        User confirmedUser = user("confirmed");
        User firstWaiting = user("first-waiting");
        User secondWaiting = user("second-waiting");
        Event event = event(1);
        Registration registration = confirmedRegistration(confirmedUser, event);
        WaitlistEntry first = entry(firstWaiting, event);
        WaitlistEntry second = entry(secondWaiting, event);

        registrations.cancelRegistration(confirmedUser.getId(), registration.getId());
        entityManager.clear();

        assertEquals(RegistrationStatus.CANCELLED,
                registrationRepository.findById(registration.getId()).orElseThrow().getStatus());
        assertEquals(WaitlistEntryStatus.OFFERED,
                waitlistEntryRepository.findById(first.getId()).orElseThrow().getStatus());
        assertEquals(WaitlistEntryStatus.WAITING,
                waitlistEntryRepository.findById(second.getId()).orElseThrow().getStatus());
        assertEquals(SlotOfferStatus.PENDING,
                slotOfferRepository.findByWaitlistEntry_IdAndStatus(
                        first.getId(), SlotOfferStatus.PENDING).orElseThrow().getStatus());
        assertCounters(event.getId(), 0, 1);
        assertEquals(1, auditCount(event.getId(), "REGISTRATION_CANCEL"));
    }

    @Test
    void removingConfirmedParticipantOffersTheFreedSlotInFifoOrder() {
        User owner = user("owner");
        User confirmedUser = user("confirmed");
        User firstWaiting = user("first-waiting");
        Event event = event(1);
        event.setOrganizerUserId(owner.getId());
        eventRepository.saveAndFlush(event);
        Registration registration = confirmedRegistration(confirmedUser, event);
        WaitlistEntry waiting = entry(firstWaiting, event);

        events.removeParticipant(owner.getId(), event.getId(), registration.getId(), "Duplicate participant");
        entityManager.clear();

        assertEquals(RegistrationStatus.REMOVED,
                registrationRepository.findById(registration.getId()).orElseThrow().getStatus());
        assertEquals(WaitlistEntryStatus.OFFERED,
                waitlistEntryRepository.findById(waiting.getId()).orElseThrow().getStatus());
        assertEquals(SlotOfferStatus.PENDING,
                slotOfferRepository.findByWaitlistEntry_IdAndStatus(
                        waiting.getId(), SlotOfferStatus.PENDING).orElseThrow().getStatus());
        assertCounters(event.getId(), 0, 1);
        assertEquals(1, auditCount(event.getId(), "PARTICIPANT_REMOVE"));
    }

    @Test
    void acceptingOfferConfirmsRegistrationAndWritesConfirmationAudit() {
        User user = user("accept");
        Event event = event(1);
        WaitlistEntry entry = entry(user, event);
        SlotOffer offer = offers.offerNextWaitingEntry(event.getId()).orElseThrow();

        offers.acceptOffer(user.getId(), offer.getId());
        entityManager.clear();

        assertEquals(RegistrationStatus.CONFIRMED,
                registrationRepository.findByUser_IdAndEvent_Id(user.getId(), event.getId())
                        .orElseThrow().getStatus());
        assertEquals(WaitlistEntryStatus.ACCEPTED,
                waitlistEntryRepository.findById(entry.getId()).orElseThrow().getStatus());
        assertEquals(SlotOfferStatus.ACCEPTED,
                slotOfferRepository.findById(offer.getId()).orElseThrow().getStatus());
        assertCounters(event.getId(), 1, 0);
        assertEquals(1, auditCount(event.getId(), "REGISTRATION_CONFIRM"));
        var history = registrations.getMyRegistrations(user.getId()).getFirst().getHistory();
        assertEquals(1, history.size());
        assertEquals("REGISTRATION_CONFIRM", history.getFirst().action());
        assertEquals("NONE", history.getFirst().previousStatus());
        assertEquals("CONFIRMED", history.getFirst().newStatus());
    }

    @Test
    void decliningOfferReleasesHeldSlotAndOffersTheNextFifoEntry() {
        User firstUser = user("decline-first");
        User secondUser = user("decline-second");
        Event event = event(1);
        WaitlistEntry first = entry(firstUser, event);
        WaitlistEntry second = entry(secondUser, event);
        SlotOffer firstOffer = offers.offerNextWaitingEntry(event.getId()).orElseThrow();

        offers.declineOffer(firstUser.getId(), firstOffer.getId());
        entityManager.clear();

        assertEquals(WaitlistEntryStatus.LEFT,
                waitlistEntryRepository.findById(first.getId()).orElseThrow().getStatus());
        assertEquals(SlotOfferStatus.DECLINED,
                slotOfferRepository.findById(firstOffer.getId()).orElseThrow().getStatus());
        assertEquals(WaitlistEntryStatus.OFFERED,
                waitlistEntryRepository.findById(second.getId()).orElseThrow().getStatus());
        assertCounters(event.getId(), 0, 1);
    }

    @Test
    void leavingAnOfferedEntryAfterExpirySkipsItAndAdvancesFifo() {
        User expiredUser = user("expired-leave");
        User nextUser = user("next-after-expiry");
        Event event = event(1);
        WaitlistEntry expiredEntry = entry(expiredUser, event);
        WaitlistEntry nextEntry = entry(nextUser, event);
        SlotOffer offer = offers.offerNextWaitingEntry(event.getId()).orElseThrow();

        LocalDateTime pastCreatedAt = LocalDateTime.now(clock).minusMinutes(2);
        LocalDateTime pastExpiresAt = LocalDateTime.now(clock).minusMinutes(1);
        jdbc.update(
                "UPDATE slot_offers SET created_at = ?, expires_at = ? WHERE id = ?",
                pastCreatedAt,
                pastExpiresAt,
                offer.getId()
        );
        entityManager.clear();

        waitlists.leaveWaitlist(expiredUser.getId(), event.getId());
        entityManager.clear();

        assertEquals(WaitlistEntryStatus.SKIPPED,
                waitlistEntryRepository.findById(expiredEntry.getId()).orElseThrow().getStatus());
        assertEquals(SlotOfferStatus.EXPIRED,
                slotOfferRepository.findById(offer.getId()).orElseThrow().getStatus());
        assertEquals(WaitlistEntryStatus.OFFERED,
                waitlistEntryRepository.findById(nextEntry.getId()).orElseThrow().getStatus());
        assertCounters(event.getId(), 0, 1);
    }

    @Test
    void directRegistrationCannotJumpAheadOfWaitingEntryWhenCancellationCreatesCapacity() {
        User confirmedUser = user("confirmed-direct-race");
        User waitingUser = user("waiting-priority");
        User directUser = user("direct-user");
        Event event = event(1);
        Registration registration = confirmedRegistration(confirmedUser, event);
        WaitlistEntry waiting = entry(waitingUser, event);

        registrations.cancelRegistration(confirmedUser.getId(), registration.getId());
        entityManager.clear();

        assertThrows(EventFullException.class,
                () -> registrations.registerForEvent(directUser.getId(), event.getId()));
        entityManager.clear();

        assertEquals(WaitlistEntryStatus.OFFERED,
                waitlistEntryRepository.findById(waiting.getId()).orElseThrow().getStatus());
        assertTrue(registrationRepository.findByUser_IdAndEvent_Id(
                directUser.getId(), event.getId()).isEmpty());
        assertCounters(event.getId(), 0, 1);
    }

    @Test
    void temporaryCloseRestoresOfferedEntryAndReopenOffersItBeforeDirectRegistration() {
        User offeredUser = user("close-offered");
        User waitingUser = user("close-waiting");
        Event event = event(1);
        WaitlistEntry offered = entry(offeredUser, event);
        WaitlistEntry waiting = entry(waitingUser, event);
        SlotOffer offer = offers.offerNextWaitingEntry(event.getId()).orElseThrow();

        event.setStatus(EventStatus.CLOSED);
        eventRepository.saveAndFlush(event);
        offers.close(event, LocalDateTime.now());
        entityManager.clear();

        assertEquals(SlotOfferStatus.REVOKED,
                slotOfferRepository.findById(offer.getId()).orElseThrow().getStatus());
        assertEquals(WaitlistEntryStatus.WAITING,
                waitlistEntryRepository.findById(offered.getId()).orElseThrow().getStatus());
        assertEquals(WaitlistEntryStatus.WAITING,
                waitlistEntryRepository.findById(waiting.getId()).orElseThrow().getStatus());
        assertCounters(event.getId(), 0, 0);

        event = eventRepository.findById(event.getId()).orElseThrow();
        event.setStatus(EventStatus.OPEN);
        eventRepository.saveAndFlush(event);
        offers.allocateAvailableSlotsToWaitlist(event.getId());
        entityManager.clear();

        assertEquals(WaitlistEntryStatus.OFFERED,
                waitlistEntryRepository.findById(offered.getId()).orElseThrow().getStatus());
        assertEquals(WaitlistEntryStatus.WAITING,
                waitlistEntryRepository.findById(waiting.getId()).orElseThrow().getStatus());
        assertCounters(event.getId(), 0, 1);
    }

    @Test
    void softDeleteCancelsAllActiveWaitlistEntriesAndRevokesOffers() {
        User offeredUser = user("delete-offered");
        User waitingUser = user("delete-waiting");
        Event event = event(1);
        WaitlistEntry offered = entry(offeredUser, event);
        WaitlistEntry waiting = entry(waitingUser, event);
        SlotOffer offer = offers.offerNextWaitingEntry(event.getId()).orElseThrow();

        event.setDeletedAt(LocalDateTime.now());
        event.setStatus(EventStatus.CLOSED);
        eventRepository.saveAndFlush(event);
        offers.delete(event, LocalDateTime.now());
        entityManager.clear();

        assertEquals(SlotOfferStatus.REVOKED,
                slotOfferRepository.findById(offer.getId()).orElseThrow().getStatus());
        assertEquals(WaitlistEntryStatus.CANCELLED,
                waitlistEntryRepository.findById(offered.getId()).orElseThrow().getStatus());
        assertEquals(WaitlistEntryStatus.CANCELLED,
                waitlistEntryRepository.findById(waiting.getId()).orElseThrow().getStatus());
        assertCounters(event.getId(), 0, 0);
    }

    @Test
    void offerExpiryIsCappedAtEventStartAndReleasesExactlyOneHeldSlot() {
        User user = user("ttl-cap");
        Event event = event(1);
        event.setOfferTtlMinutes(30);
        event.setStartTime(LocalDateTime.now(clock).plusMinutes(5).truncatedTo(ChronoUnit.MICROS));
        event.setEndTime(event.getStartTime().plusHours(1));
        eventRepository.saveAndFlush(event);
        entry(user, event);

        SlotOffer offer = offers.offerNextWaitingEntry(event.getId()).orElseThrow();

        assertEquals(event.getStartTime(), offer.getExpiresAt());
        assertTrue(offer.getExpiresAt().isBefore(offer.getCreatedAt().plusMinutes(30)));
        assertEquals(1, eventRepository.findById(event.getId()).orElseThrow().getHeldCount());

        offers.expireOffers(event.getStartTime());
        entityManager.clear();

        assertEquals(SlotOfferStatus.EXPIRED,
                slotOfferRepository.findById(offer.getId()).orElseThrow().getStatus());
        assertCounters(event.getId(), 0, 0);
    }

    @Test
    void repeatedExpiryDoesNotReleaseHeldCountTwice() {
        User user = user("idempotent-expiry");
        Event event = event(1);
        entry(user, event);
        SlotOffer offer = offers.offerNextWaitingEntry(event.getId()).orElseThrow();
        LocalDateTime afterExpiry = offer.getExpiresAt().plusSeconds(1);

        assertEquals(1, offers.expireOffers(afterExpiry));
        assertEquals(0, offers.expireOffers(afterExpiry));
        entityManager.clear();

        assertEquals(SlotOfferStatus.EXPIRED,
                slotOfferRepository.findById(offer.getId()).orElseThrow().getStatus());
        assertCounters(event.getId(), 0, 0);
    }

    @Test
    void cancelledRegistrationCanReRegisterWithAConfirmationAuditAndFreshTimestamp() {
        User user = user("reregister");
        Event event = event(2);
        registrations.registerForEvent(user.getId(), event.getId());
        Registration registration = registrationRepository
                .findByUser_IdAndEvent_Id(user.getId(), event.getId())
                .orElseThrow();
        LocalDateTime originalRegisteredAt = registration.getRegisteredAt();

        registrations.cancelRegistration(user.getId(), registration.getId());
        registrations.registerForEvent(user.getId(), event.getId());
        entityManager.clear();

        Registration saved = registrationRepository.findById(registration.getId()).orElseThrow();
        assertEquals(RegistrationStatus.CONFIRMED, saved.getStatus());
        assertNull(saved.getCancelledAt());
        assertTrue(saved.getRegisteredAt().isAfter(originalRegisteredAt));
        assertEquals(2, auditCount(event.getId(), "REGISTRATION_CONFIRM"));
        assertEquals(1, auditCount(event.getId(), "REGISTRATION_CANCEL"));
        assertCounters(event.getId(), 1, 0);
    }

    private User user(String suffix) {
        User user = userRepository.saveAndFlush(new User(
                "week3-" + suffix + "-" + UUID.randomUUID() + "@test.com",
                "Week 3 " + suffix, "hash", null, Role.USER));
        userIds.add(user.getId());
        return user;
    }

    private Event event(int capacity) {
        Event event = new Event();
        event.setTitle("Week 3 acceptance event");
        event.setOrganizerName("Test Organizer");
        event.setCity(City.HANOI);
        event.setCategory(Category.TECHNOLOGY);
        event.setStartTime(LocalDateTime.now(clock).plusHours(2));
        event.setEndTime(event.getStartTime().plusHours(1));
        event.setCapacity(capacity);
        event.setRegisteredCount(0);
        event.setHeldCount(0);
        event.setStatus(EventStatus.OPEN);
        Event saved = eventRepository.saveAndFlush(event);
        eventIds.add(saved.getId());
        return saved;
    }

    private Registration confirmedRegistration(User user, Event event) {
        Registration registration = registrationRepository.saveAndFlush(
                new Registration(user, event, LocalDateTime.now()));
        event.setRegisteredCount(event.getRegisteredCount() + 1);
        eventRepository.saveAndFlush(event);
        return registration;
    }

    private WaitlistEntry entry(User user, Event event) {
        return waitlistEntryRepository.saveAndFlush(new WaitlistEntry(user, event));
    }

    private void assertCounters(Long eventId, int registered, int held) {
        Event saved = eventRepository.findById(eventId).orElseThrow();
        assertEquals(registered, saved.getRegisteredCount());
        assertEquals(held, saved.getHeldCount());
        assertTrue(saved.getRegisteredCount() >= 0);
        assertTrue(saved.getHeldCount() >= 0);
        assertTrue(saved.getRegisteredCount() + saved.getHeldCount() <= saved.getCapacity());
    }

    private int auditCount(Long eventId, String action) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE target_type='EVENT' AND target_id=? AND action=?",
                Integer.class, eventId, action);
    }
}

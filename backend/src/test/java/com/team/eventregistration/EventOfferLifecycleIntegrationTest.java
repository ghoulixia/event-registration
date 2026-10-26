package com.team.eventregistration;

import com.team.eventregistration.dto.CreateEventRequest;
import com.team.eventregistration.dto.EventResponse;
import com.team.eventregistration.dto.OwnerWaitlistResponse;
import com.team.eventregistration.dto.UpdateEventRequest;
import com.team.eventregistration.entity.Event;
import com.team.eventregistration.entity.EventStatus;
import com.team.eventregistration.entity.RegistrationStatus;
import com.team.eventregistration.entity.Role;
import com.team.eventregistration.entity.User;
import com.team.eventregistration.repository.EventRepository;
import com.team.eventregistration.repository.RegistrationRepository;
import com.team.eventregistration.repository.UserRepository;
import com.team.eventregistration.service.EventService;
import com.team.eventregistration.service.RegistrationService;
import com.team.eventregistration.service.port.EventOfferLifecycle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(EventOfferLifecycleIntegrationTest.OfferFixtureConfiguration.class)
class EventOfferLifecycleIntegrationTest extends PostgresTestSupport {
    @Autowired EventService events;
    @Autowired RegistrationService registrations;
    @Autowired EventRepository eventRepository;
    @Autowired RegistrationRepository registrationRepository;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired OfferFixture fixture;
    User owner;
    User attendee;

    @BeforeEach void prepare() {
        fixture.reset();
        owner = user(); attendee = user();
    }

    @Test void capacityGrowthCloseAndReopenDelegateWithinTheEventTransaction() {
        EventResponse event = event(1);
        fixture.allocateOne = true;
        UpdateEventRequest update = update(event); update.setCapacity(2);
        events.updateEvent(owner.getId(), event.getId(), update);
        assertCounts(event.getId(), 0, 1);
        events.updateStatus(owner.getId(), event.getId(), "CLOSED");
        assertCounts(event.getId(), 0, 0);
        assertEquals(EventStatus.CLOSED, persisted(event.getId()).getStatus());
        events.updateStatus(owner.getId(), event.getId(), "OPEN");
        assertCounts(event.getId(), 0, 1);
        assertEquals(List.of("allocate", "close", "allocate"), fixture.calls);
    }

    @Test void deletionDelegatesAfterClosingAndKeepsConfirmedRegistrations() {
        EventResponse event = event(2);
        long registrationId = registrations.registerForEvent(attendee.getId(), event.getId()).getId();
        jdbc.update("UPDATE events SET held_count=1 WHERE id=?", event.getId());
        events.softDeleteEvent(owner.getId(), event.getId(), "Venue unavailable");
        Event deleted = persisted(event.getId());
        assertNotNull(deleted.getDeletedAt());
        assertEquals(EventStatus.CLOSED, deleted.getStatus());
        assertCounts(event.getId(), 1, 0);
        assertEquals(RegistrationStatus.CONFIRMED, registrationRepository.findById(registrationId).orElseThrow().getStatus());
        assertEquals(List.of("delete"), fixture.calls);
        assertEquals(1, auditCount(event.getId(), "EVENT_DELETE"));
    }

    @Test void removalDelegatesAllocationOnceAndRecordsTheActualParticipant() {
        EventResponse event = event(1);
        long registrationId = registrations.registerForEvent(attendee.getId(), event.getId()).getId();
        fixture.allocateOne = true;
        events.removeParticipant(owner.getId(), event.getId(), registrationId, "Duplicate participant");
        events.removeParticipant(owner.getId(), event.getId(), registrationId, "Retry");
        var removed = registrationRepository.findById(registrationId).orElseThrow();
        assertEquals(RegistrationStatus.REMOVED, removed.getStatus());
        assertEquals(owner.getId(), removed.getRemovedByUserId());
        assertEquals("Duplicate participant", removed.getRemovedReason());
        assertCounts(event.getId(), 0, 1);
        assertEquals(List.of("allocate"), fixture.calls);
        assertEquals(1, auditCount(event.getId(), "PARTICIPANT_REMOVE"));
        assertEquals(registrationId, jdbc.queryForObject(
                "SELECT (details->>'registrationId')::bigint FROM audit_logs WHERE target_id=? AND action='PARTICIPANT_REMOVE'",
                Long.class, event.getId()));
    }

    @Test void providerFailureRollsBackRemovalAndBothCounters() {
        EventResponse event = event(1);
        long registrationId = registrations.registerForEvent(attendee.getId(), event.getId()).getId();
        fixture.allocateOne = true; fixture.failAfterAllocation = true;
        assertThrows(IllegalStateException.class, () ->
                events.removeParticipant(owner.getId(), event.getId(), registrationId, "Requested removal"));
        assertCounts(event.getId(), 1, 0);
        var registration = registrationRepository.findById(registrationId).orElseThrow();
        assertEquals(RegistrationStatus.CONFIRMED, registration.getStatus());
        assertNull(registration.getRemovedAt());
        assertEquals(0, auditCount(event.getId(), "PARTICIPANT_REMOVE"));
        assertEquals(List.of("allocate"), fixture.calls);
    }

    @Test void auditFailureRollsBackCapacityAndTheProvidersHeldCounter() {
        EventResponse event = event(1); fixture.allocateOne = true;
        UpdateEventRequest update = update(event); update.setCapacity(2);
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT reject_offer_fixture_update CHECK(action <> 'EVENT_UPDATE') NOT VALID");
        try {
            assertThrows(RuntimeException.class, () -> events.updateEvent(owner.getId(), event.getId(), update));
            assertEquals(1, persisted(event.getId()).getCapacity());
            assertCounts(event.getId(), 0, 0);
            assertEquals(0, auditCount(event.getId(), "EVENT_UPDATE"));
            assertEquals(List.of("allocate"), fixture.calls);
        } finally { jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT reject_offer_fixture_update"); }
    }

    User user() { return users.saveAndFlush(new User(UUID.randomUUID()+"@test.local", "Lifecycle user", "unused-hash", null, Role.USER)); }
    EventResponse event(int capacity) {
        CreateEventRequest request = new CreateEventRequest();
        request.setTitle("Lifecycle contract event"); request.setOrganizerName("Community");
        request.setCity("HANOI"); request.setCategory("EDUCATION"); request.setCapacity(capacity);
        request.setStartTime(LocalDateTime.now(clock).plusYears(10)); request.setEndTime(request.getStartTime().plusHours(2));
        request.setOfferTtlMinutes(10);
        return events.createEvent(owner.getId(), request);
    }
    UpdateEventRequest update(EventResponse event) {
        UpdateEventRequest request = new UpdateEventRequest();
        request.setTitle(event.getTitle()); request.setOrganizerName(event.getOrganizerName());
        request.setCity(event.getCity()); request.setCategory(event.getCategory()); request.setCapacity(event.getCapacity());
        request.setStartTime(event.getStartTime()); request.setEndTime(event.getEndTime()); request.setOfferTtlMinutes(event.getOfferTtlMinutes());
        return request;
    }
    Event persisted(Long id) { return eventRepository.findById(id).orElseThrow(); }
    void assertCounts(Long id, int confirmed, int held) {
        Event event = persisted(id);
        assertEquals(confirmed, event.getRegisteredCount()); assertEquals(held, event.getHeldCount());
    }
    int auditCount(Long eventId, String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE target_type='EVENT' AND target_id=? AND action=?",
                Integer.class, eventId, action);
    }

    @TestConfiguration static class OfferFixtureConfiguration {
        @Bean OfferFixture offerFixture() { return new OfferFixture(); }
    }

    // Simulates allocation to verify Event transaction boundaries.
    static class OfferFixture implements EventOfferLifecycle {
        final List<String> calls = new ArrayList<>();
        boolean allocateOne;
        boolean failAfterAllocation;
        void reset() { calls.clear(); allocateOne=false; failAfterAllocation=false; }
        void called(String action) {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), "Lifecycle hook must join the Event transaction");
            calls.add(action);
        }
        public void allocateAvailable(Event event, LocalDateTime now) {
            called("allocate");
            if (allocateOne && event.getRegisteredCount()+event.getHeldCount()<event.getCapacity()) event.setHeldCount(event.getHeldCount()+1);
            if (failAfterAllocation) throw new IllegalStateException("Offer provider failed");
        }
        public void close(Event event, LocalDateTime now) {
            called("close"); assertEquals(EventStatus.CLOSED, event.getStatus()); event.setHeldCount(0);
        }
        public void delete(Event event, LocalDateTime now) {
            called("delete"); assertEquals(EventStatus.CLOSED, event.getStatus()); assertNotNull(event.getDeletedAt()); event.setHeldCount(0);
        }
        public Optional<LocalDateTime> latestPendingExpiry(Event event) { return Optional.empty(); }
        public long waitingCount(Event event) { return 0; }
        public List<OwnerWaitlistResponse> waitingList(Event event) { return List.of(); }
    }
}

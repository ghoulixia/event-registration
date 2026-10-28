package com.team.eventregistration;

import com.team.eventregistration.dto.*;
import com.team.eventregistration.entity.*;
import com.team.eventregistration.exception.BusinessRuleException;
import com.team.eventregistration.exception.DuplicateRegistrationException;
import com.team.eventregistration.exception.EventConflictException;
import com.team.eventregistration.repository.*;
import com.team.eventregistration.security.JwtService;
import com.team.eventregistration.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Import(EventOwnershipIntegrationTest.TimeConfiguration.class)
@Timeout(60)
class EventOwnershipIntegrationTest extends PostgresTestSupport {
    @Autowired EventService events;
    @Autowired RegistrationService registrations;
    @Autowired EventRepository eventRepository;
    @Autowired RegistrationRepository registrationRepository;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired MutableClock clock;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired WebApplicationContext context;
    @Value("${app.upload.dir}") String uploadDirectory;
    MockMvc mvc;
    User owner;
    User other;
    final byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @BeforeEach
    void setUp() {
        clock.reset();
        owner = user("Owner");
        other = user("Attendee");
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    User user(String name) {
        return users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", name, "unused-hash", null, Role.USER));
    }

    CreateEventRequest request(int capacity) {
        CreateEventRequest request = new CreateEventRequest();
        request.setTitle("Community event " + UUID.randomUUID());
        request.setOrganizerName("Community");
        request.setCity("HANOI");
        request.setCategory("MUSIC");
        request.setStartTime(LocalDateTime.now(clock).plusDays(1));
        request.setEndTime(request.getStartTime().plusHours(3));
        request.setCapacity(capacity);
        request.setOfferTtlMinutes(10);
        return request;
    }

    EventResponse event(int capacity) { return events.createEvent(owner.getId(), request(capacity)); }
    String token(User user) { return "Bearer " + jwt.issue(user.getId()); }
    String path(EventResponse event) { return "/api/events/" + event.getId(); }

    UpdateEventRequest update(EventResponse event) {
        UpdateEventRequest request = new UpdateEventRequest();
        request.setTitle("Updated title");
        request.setDescription(event.getDescription());
        request.setOrganizerName(event.getOrganizerName());
        request.setCity(event.getCity());
        request.setCategory(event.getCategory());
        request.setStartTime(event.getStartTime());
        request.setEndTime(event.getEndTime());
        request.setCapacity(event.getCapacity());
        request.setOfferTtlMinutes(event.getOfferTtlMinutes());
        return request;
    }

    RegistrationResponse register(User user, EventResponse event) {
        return registrations.registerForEvent(user.getId(), event.getId());
    }

    void assertConfirmedCount(Long eventId, int expected) {
        assertEquals(expected, jdbc.queryForObject("select registered_count from events where id=?", Integer.class, eventId));
        assertEquals(expected, jdbc.queryForObject("select count(*) from registrations where event_id=? and status='CONFIRMED'", Integer.class, eventId));
    }

    long auditCount(Long eventId, String action) {
        return jdbc.queryForObject("select count(*) from audit_logs where target_type='EVENT' and target_id=? and action=?", Long.class, eventId, action);
    }

    @Test
    void privateEventEndpointsRequireAuthenticationAndTheCorrectOwner() throws Exception {
        EventResponse event = event(2);
        mvc.perform(get(path(event))).andExpect(status().isOk()).andExpect(jsonPath("$.organizerUserId").value(owner.getId()));
        for (String suffix : List.of("/registrations", "/waitlist", "/audit-logs")) {
            mvc.perform(get(path(event) + suffix)).andExpect(status().isUnauthorized());
            mvc.perform(get(path(event) + suffix).header("Authorization", token(other))).andExpect(status().isForbidden());
            mvc.perform(get(path(event) + suffix).header("Authorization", token(owner))).andExpect(status().isOk());
        }
        mvc.perform(patch(path(event) + "/status").header("Authorization", token(other))
                .contentType("application/json").content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isForbidden());
        assertEquals(EventStatus.OPEN, eventRepository.findById(event.getId()).orElseThrow().getStatus());
    }

    @Test
    void legacyAdminRoutesCannotBypassOwnerAuthorization() throws Exception {
        EventResponse event = event(2);
        mvc.perform(delete("/api/admin/events/" + event.getId()).header("Authorization", token(other)))
                .andExpect(status().isForbidden());
        assertNull(eventRepository.findById(event.getId()).orElseThrow().getDeletedAt());
    }

    @Test
    void creationAndTtlConfigurationAcceptTheSpecifiedRange() {
        for (int ttl : new int[]{5, 30}) {
            CreateEventRequest request = request(2);
            request.setOfferTtlMinutes(ttl);
            EventResponse created = events.createEvent(owner.getId(), request);
            assertEquals(owner.getId(), created.getOrganizerUserId());
            assertEquals(ttl, eventRepository.findById(created.getId()).orElseThrow().getOfferTtlMinutes());
            assertEquals(EventStatus.OPEN, eventRepository.findById(created.getId()).orElseThrow().getStatus());
        }
    }

    @Test
    void ownedListIncludesDeletedEventsButNotAnotherUsersEvents() throws Exception {
        EventResponse active = event(2);
        EventResponse deleted = event(2);
        events.createEvent(other.getId(), request(2));
        events.softDeleteEvent(owner.getId(), deleted.getId(), "Venue unavailable");
        var owned = events.myEvents(owner.getId(), 0, 12);
        assertEquals(Set.of(active.getId(), deleted.getId()), owned.content().stream().map(EventResponse::getId).collect(Collectors.toSet()));
        mvc.perform(get("/api/me/events").header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void metadataAndCapacityCanChangeAfterConfirmedRegistrationHistory() {
        EventResponse event = event(2);
        register(other, event);
        UpdateEventRequest request = update(event);
        request.setCapacity(1);
        request.setOfferTtlMinutes(30);
        events.updateEvent(owner.getId(), event.getId(), request);
        assertEquals(1, events.getOwnedEvent(owner.getId(), event.getId()).getCapacity());
        request.setCapacity(3);
        EventResponse updated = events.updateEvent(owner.getId(), event.getId(), request);
        assertEquals(3, updated.getCapacity());
        assertEquals(30, updated.getOfferTtlMinutes());
        assertEquals("Updated title", updated.getTitle());
        assertConfirmedCount(event.getId(), 1);
        AuditResponse audit = events.auditHistory(owner.getId(), event.getId()).stream()
                .filter(item -> item.action().equals("EVENT_UPDATE")).findFirst().orElseThrow();
        Map<?, ?> before = (Map<?, ?>) audit.details().get("before");
        Map<?, ?> after = (Map<?, ?>) audit.details().get("after");
        assertEquals(1, ((Number) before.get("capacity")).intValue());
        assertEquals(3, ((Number) after.get("capacity")).intValue());
    }

    @Test
    void capacityCannotBeReducedBelowTheActualConfirmedCount() {
        EventResponse event = event(3);
        register(other, event);
        register(user("Second attendee"), event);
        UpdateEventRequest request = update(event);
        request.setCapacity(1);
        assertThrows(EventConflictException.class, () -> events.updateEvent(owner.getId(), event.getId(), request));
        assertEquals(3, eventRepository.findById(event.getId()).orElseThrow().getCapacity());
        assertConfirmedCount(event.getId(), 2);
    }

    @Test
    void confirmedRegistrationsCanBeCancelledWhileTheEventIsTemporarilyClosed() {
        EventResponse event = event(2);
        RegistrationResponse registration = register(other, event);
        events.updateStatus(owner.getId(), event.getId(), "CLOSED");
        registrations.cancelRegistration(other.getId(), registration.getId());
        assertConfirmedCount(event.getId(), 0);
        assertEquals(LocalDateTime.now(clock), registrationRepository.findById(registration.getId()).orElseThrow().getCancelledAt());
        events.updateStatus(owner.getId(), event.getId(), "OPEN");
        assertTrue(events.getEventDetail(event.getId()).getCanRegister());
    }

    @Test
    void deletionHidesTheEventButPreservesRegistrationsAndOwnerHistory() throws Exception {
        EventResponse event = event(2);
        RegistrationResponse registration = register(other, event);
        events.softDeleteEvent(owner.getId(), event.getId(), "Venue unavailable");
        events.softDeleteEvent(owner.getId(), event.getId(), "Retry");
        mvc.perform(get(path(event))).andExpect(status().isNotFound());
        mvc.perform(get("/api/me/events/" + event.getId()).header("Authorization", token(owner))).andExpect(status().isOk());
        mvc.perform(get(path(event) + "/registrations").header("Authorization", token(owner))).andExpect(status().isOk());
        mvc.perform(get(path(event) + "/audit-logs").header("Authorization", token(other))).andExpect(status().isForbidden());
        assertTrue(registrationRepository.existsById(registration.getId()));
        assertConfirmedCount(event.getId(), 1);
        assertEquals(1, auditCount(event.getId(), "EVENT_DELETE"));
        assertFalse(events.auditHistory(owner.getId(), event.getId()).isEmpty());
    }

    @Test
    void ownerCanRemoveAParticipantAndRepeatingTheRequestDoesNotReleaseTwice() throws Exception {
        EventResponse event = event(2);
        RegistrationResponse registration = register(other, event);
        String removal = path(event) + "/registrations/" + registration.getId();
        mvc.perform(delete(removal).header("Authorization", token(owner)).contentType("application/json")
                .content("{\"reason\":\"Duplicate participant\"}")).andExpect(status().isNoContent());
        mvc.perform(delete(removal).header("Authorization", token(owner)).contentType("application/json")
                .content("{\"reason\":\"Duplicate participant\"}")).andExpect(status().isNoContent());
        Registration removed = registrationRepository.findById(registration.getId()).orElseThrow();
        assertEquals(RegistrationStatus.REMOVED, removed.getStatus());
        assertEquals("Duplicate participant", removed.getRemovedReason());
        assertEquals(owner.getId(), removed.getRemovedByUserId());
        assertNotNull(removed.getRemovedAt());
        assertConfirmedCount(event.getId(), 0);
        assertEquals(1, auditCount(event.getId(), "PARTICIPANT_REMOVE"));
        mvc.perform(post(path(event) + "/registrations").header("Authorization", token(other)))
                .andExpect(status().isConflict());
    }

    @Test
    void ownerCannotRegisterForTheirOwnEvent() throws Exception {
        EventResponse event = event(1);
        mvc.perform(post(path(event) + "/registrations").header("Authorization", token(owner)))
                .andExpect(status().isConflict()); // BusinessRuleException results in 409 Conflict
        assertConfirmedCount(event.getId(), 0);
    }

    @Test
    void removalCannotTargetARegistrationInAnotherEvent() throws Exception {
        EventResponse first = event(2);
        EventResponse second = event(2);
        RegistrationResponse registration = register(other, second);
        mvc.perform(delete(path(first) + "/registrations/" + registration.getId()).header("Authorization", token(owner))
                .contentType("application/json").content("{\"reason\":\"Reason\"}"))
                .andExpect(status().isNotFound());
        assertEquals(RegistrationStatus.CONFIRMED, registrationRepository.findById(registration.getId()).orElseThrow().getStatus());
        assertConfirmedCount(second.getId(), 1);
    }

    @Test
    void removalAtTheStartTimeLeavesTheParticipantAndCounterUntouched() throws Exception {
        EventResponse event = event(2);
        RegistrationResponse registration = register(other, event);
        Event started = eventRepository.findById(event.getId()).orElseThrow();
        started.setStartTime(LocalDateTime.now(clock));
        eventRepository.saveAndFlush(started);
        mvc.perform(delete(path(event) + "/registrations/" + registration.getId()).header("Authorization", token(owner))
                .contentType("application/json").content("{\"reason\":\"Reason\"}"))
                .andExpect(status().isConflict());
        assertEquals(RegistrationStatus.CONFIRMED, registrationRepository.findById(registration.getId()).orElseThrow().getStatus());
        assertConfirmedCount(event.getId(), 1);
    }

    @Test
    void blankReasonsAndMissingThumbnailReturnValidationErrors() throws Exception {
        EventResponse event = event(2);
        RegistrationResponse registration = register(other, event);
        mvc.perform(delete(path(event)).header("Authorization", token(owner)).contentType("application/json")
                .content("{\"reason\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(delete(path(event) + "/registrations/" + registration.getId()).header("Authorization", token(owner))
                .contentType("application/json").content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(path(event) + "/thumbnail").header("Authorization", token(owner))).andExpect(status().isBadRequest());
        assertNull(eventRepository.findById(event.getId()).orElseThrow().getDeletedAt());
        assertConfirmedCount(event.getId(), 1);
    }

    @Test
    void failedAuditWriteRollsBackAnEventUpdate() {
        EventResponse event = event(2);
        register(other, event);
        UpdateEventRequest request = update(event);
        request.setCapacity(3);
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT reject_owner_update CHECK(action <> 'EVENT_UPDATE') NOT VALID");
        try {
            assertThrows(RuntimeException.class, () -> events.updateEvent(owner.getId(), event.getId(), request));
            Event unchanged = eventRepository.findById(event.getId()).orElseThrow();
            assertEquals(2, unchanged.getCapacity());
            assertEquals(event.getTitle(), unchanged.getTitle());
            assertConfirmedCount(event.getId(), 1);
        } finally { jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT reject_owner_update"); }
    }

    @Test
    void failedRemovalAuditRollsBackTheRegistrationAndItsCounter() {
        EventResponse event = event(2);
        RegistrationResponse registration = register(other, event);
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT reject_owner_removal CHECK(action <> 'PARTICIPANT_REMOVE') NOT VALID");
        try {
            assertThrows(RuntimeException.class, () -> events.removeParticipant(owner.getId(), event.getId(), registration.getId(), "Reason"));
            Registration unchanged = registrationRepository.findById(registration.getId()).orElseThrow();
            assertEquals(RegistrationStatus.CONFIRMED, unchanged.getStatus());
            assertNull(unchanged.getRemovedAt());
            assertConfirmedCount(event.getId(), 1);
            assertEquals(0, auditCount(event.getId(), "PARTICIPANT_REMOVE"));
        } finally { jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT reject_owner_removal"); }
    }

    @Test
    void uploadedThumbnailIsPublicAndUsesTheGeneratedFilename() throws Exception {
        EventResponse event = event(2);
        MockMultipartFile file = new MockMultipartFile("file", "untrusted-name.html", "image/png", png);
        mvc.perform(multipart(path(event) + "/thumbnail").file(file).header("Authorization", token(owner)))
                .andExpect(status().isOk());
        String stored = eventRepository.findById(event.getId()).orElseThrow().getThumbnailPath();
        assertTrue(stored.endsWith(".png"));
        assertNotEquals("untrusted-name.html", stored);
        mvc.perform(get("/api/uploads/thumbnails/" + stored)).andExpect(status().isOk()).andExpect(content().bytes(png));
        assertTrue(Files.exists(Path.of(uploadDirectory).resolve(stored)));
        mvc.perform(multipart(path(event) + "/thumbnail").file(file).header("Authorization", token(other)))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadRollbackKeepsTheOldImageAndDeletesTheNewFile() throws Exception {
        EventResponse event = event(2);
        EventResponse withImage = events.uploadThumbnail(owner.getId(), event.getId(), new ByteArrayInputStream(png), "first.png", "image/png", png.length);
        String oldFile = eventRepository.findById(event.getId()).orElseThrow().getThumbnailPath();
        Path directory = Path.of(uploadDirectory);
        Set<String> before;
        try (var files = Files.list(directory)) { before = files.map(file -> file.getFileName().toString()).collect(Collectors.toSet()); }
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT reject_owner_thumbnail CHECK(action <> 'EVENT_UPDATE') NOT VALID");
        try {
            assertThrows(RuntimeException.class, () -> events.uploadThumbnail(owner.getId(), event.getId(), new ByteArrayInputStream(png), "second.png", "image/png", png.length));
            assertEquals(withImage.getThumbnailUrl(), events.getOwnedEvent(owner.getId(), event.getId()).getThumbnailUrl());
            assertTrue(Files.exists(directory.resolve(oldFile)));
            try (var files = Files.list(directory)) { assertEquals(before, files.map(file -> file.getFileName().toString()).collect(Collectors.toSet())); }
        } finally { jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT reject_owner_thumbnail"); }
    }

    @Test
    void eventsWithoutVerifiedOwnershipRemainPublicButCannotBeManaged() throws Exception {
        EventResponse event = event(2);
        Event legacy = eventRepository.findById(event.getId()).orElseThrow();
        legacy.setOrganizerUserId(null);
        eventRepository.saveAndFlush(legacy);
        mvc.perform(get(path(event))).andExpect(status().isOk());
        mvc.perform(get("/api/me/events/" + event.getId()).header("Authorization", token(owner)))
                .andExpect(status().isForbidden());
        assertNull(eventRepository.findById(event.getId()).orElseThrow().getOrganizerUserId());
    }

    @Test
    void metadataUpdateDoesNotOverwriteARegistrationCommittedWhileItWaitsForTheLock() throws Exception {
        EventResponse event = event(2);
        UpdateEventRequest request = update(event);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        List<Future<EventResponse>> result = new ArrayList<>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                Event locked = eventRepository.findByIdForUpdate(event.getId()).orElseThrow();
                result.add(executor.submit(() -> {
                    started.countDown();
                    return events.updateEvent(owner.getId(), event.getId(), request);
                }));
                try {
                    assertTrue(started.await(5, TimeUnit.SECONDS));
                    assertThrows(TimeoutException.class, () -> result.getFirst().get(200, TimeUnit.MILLISECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                registrationRepository.saveAndFlush(new Registration(other, locked));
                locked.setRegisteredCount(1);
                eventRepository.saveAndFlush(locked);
            });
            assertEquals("Updated title", result.getFirst().get(10, TimeUnit.SECONDS).getTitle());
            assertConfirmedCount(event.getId(), 1);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentRegistrationRequestsReactivateACancelledRowOnlyOnce() throws Exception {
        EventResponse event = event(3);
        RegistrationResponse original = register(other, event);
        registrations.cancelRegistration(other.getId(), original.getId());
        assertConfirmedCount(event.getId(), 0);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch gate = new CountDownLatch(1);
        try {
            Callable<Boolean> registerAgain = () -> {
                gate.await();
                try {
                    RegistrationResponse confirmed = register(other, event);
                    assertEquals(original.getId(), confirmed.getId());
                    return true;
                } catch (DuplicateRegistrationException duplicate) {
                    return false;
                }
            };
            List<Future<Boolean>> results = List.of(executor.submit(registerAgain), executor.submit(registerAgain));
            gate.countDown();
            int successes = 0;
            for (Future<Boolean> result : results) if (result.get(10, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
            assertEquals(1, jdbc.queryForObject("select count(*) from registrations where event_id=? and user_id=?", Integer.class,
                    event.getId(), other.getId()));
            assertConfirmedCount(event.getId(), 1);
        } finally {
            gate.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void registrationWaitingForTheEventLockMustRecheckTheStartTime() throws Exception {
        EventResponse event = event(2);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        List<Future<BusinessRuleException>> result = new ArrayList<>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                eventRepository.findByIdForUpdate(event.getId()).orElseThrow();
                result.add(executor.submit(() -> {
                    started.countDown();
                    try {
                        register(other, event);
                        return null;
                    } catch (BusinessRuleException rejected) {
                        return rejected;
                    }
                }));
                try {
                    assertTrue(started.await(5, TimeUnit.SECONDS));
                    assertThrows(TimeoutException.class, () -> result.getFirst().get(200, TimeUnit.MILLISECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                clock.set(event.getStartTime().atZone(clock.getZone()).toInstant());
            });
            BusinessRuleException rejected = result.getFirst().get(10, TimeUnit.SECONDS);
            assertNotNull(rejected);
            assertTrue(rejected.getMessage().contains("started"));
            assertConfirmedCount(event.getId(), 0);
            assertEquals(0, jdbc.queryForObject("select count(*) from registrations where event_id=?", Integer.class, event.getId()));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> value = new AtomicReference<>(Instant.parse("2030-01-01T00:00:00Z"));
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Ho_Chi_Minh"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return value.get(); }
        void set(Instant instant) { value.set(instant); }
        void reset() { set(Instant.parse("2030-01-01T00:00:00Z")); }
    }
}

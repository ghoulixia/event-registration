package com.team.eventregistration.service;

import com.team.eventregistration.entity.Category;
import com.team.eventregistration.entity.City;
import com.team.eventregistration.entity.Event;
import com.team.eventregistration.entity.EventStatus;
import com.team.eventregistration.entity.Registration;
import com.team.eventregistration.entity.RegistrationStatus;
import com.team.eventregistration.entity.Role;
import com.team.eventregistration.entity.SlotOfferStatus;
import com.team.eventregistration.entity.User;
import com.team.eventregistration.entity.WaitlistEntry;
import com.team.eventregistration.entity.WaitlistEntryStatus;
import com.team.eventregistration.exception.BusinessRuleException;
import com.team.eventregistration.repository.EventRepository;
import com.team.eventregistration.repository.RegistrationRepository;
import com.team.eventregistration.repository.SlotOfferRepository;
import com.team.eventregistration.repository.UserRepository;
import com.team.eventregistration.repository.WaitlistEntryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class RegistrationCancellationIntegrationTest {
    @Autowired private RegistrationService registrationService;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private SlotOfferRepository slotOfferRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WaitlistEntryRepository waitlistEntryRepository;
    @Autowired private EntityManager entityManager;

    private User user;
    private Event event;
    private Registration registration;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(new User(
                UUID.randomUUID() + "@test.com",
                "Cancellation Test User",
                "test-password",
                "0900000000",
                Role.USER));

        event = new Event();
        event.setTitle("Cancellation Integration Test");
        event.setOrganizerName("Test Organizer");
        event.setCity(City.HANOI);
        event.setCategory(Category.TECHNOLOGY);
        event.setStartTime(LocalDateTime.now().plusDays(1));
        event.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        event.setCapacity(10);
        event.setRegisteredCount(1);
        event.setStatus(EventStatus.OPEN);
        event = eventRepository.saveAndFlush(event);

        registration = registrationRepository.saveAndFlush(new Registration(user, event));
    }

    @AfterEach
    void tearDown() {
        if (registration != null && registration.getId() != null) {
            registrationRepository.deleteById(registration.getId());
        }
        if (event != null && event.getId() != null) {
            eventRepository.deleteById(event.getId());
        }
        if (user != null && user.getId() != null) {
            userRepository.deleteById(user.getId());
        }
    }

    @Test
    void shouldRollbackRegistrationWhenCounterCannotBeDecremented() {
        event.setRegisteredCount(0);
        eventRepository.saveAndFlush(event);

        assertThrows(
                BusinessRuleException.class,
                () -> registrationService.cancelRegistration(user.getId(), registration.getId()));

        entityManager.clear();
        Registration unchanged = registrationRepository.findById(registration.getId()).orElseThrow();
        Event unchangedEvent = eventRepository.findById(event.getId()).orElseThrow();

        assertEquals(RegistrationStatus.CONFIRMED, unchanged.getStatus());
        assertNull(unchanged.getCancelledAt());
        assertEquals(0, unchangedEvent.getRegisteredCount());
    }

    @Test
    void shouldCancelOnlyOnceWhenRequestsAreConcurrent() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<Void>> results = executor.invokeAll(List.of(
                    () -> cancelAndReturnNull(),
                    () -> cancelAndReturnNull()));

            for (java.util.concurrent.Future<Void> result : results) {
                try {
                    result.get();
                } catch (ExecutionException ex) {
                    throw (Exception) ex.getCause();
                }
            }
        } finally {
            executor.shutdownNow();
        }

        entityManager.clear();
        Registration cancelled = registrationRepository.findById(registration.getId()).orElseThrow();
        Event updatedEvent = eventRepository.findById(event.getId()).orElseThrow();

        assertEquals(RegistrationStatus.CANCELLED, cancelled.getStatus());
        assertNotNull(cancelled.getCancelledAt());
        assertEquals(0, updatedEvent.getRegisteredCount());
    }

    @Test
    void shouldOfferReleasedSlotToFirstWaitingEntry() {
        User waitingUser = userRepository.saveAndFlush(new User(
                UUID.randomUUID() + "@test.com",
                "Waiting User",
                "test-password",
                null,
                Role.USER));
        WaitlistEntry waitingEntry = waitlistEntryRepository.saveAndFlush(
                new WaitlistEntry(waitingUser, event));

        registrationService.cancelRegistration(user.getId(), registration.getId());
        entityManager.clear();

        assertEquals(RegistrationStatus.CANCELLED,
                registrationRepository.findById(registration.getId()).orElseThrow().getStatus());
        assertEquals(WaitlistEntryStatus.OFFERED,
                waitlistEntryRepository.findById(waitingEntry.getId()).orElseThrow().getStatus());
        assertEquals(SlotOfferStatus.PENDING,
                slotOfferRepository.findByWaitlistEntry_IdAndStatus(
                        waitingEntry.getId(), SlotOfferStatus.PENDING).orElseThrow().getStatus());

        slotOfferRepository.deleteAll(slotOfferRepository.findByEvent_Id(event.getId()));
        waitlistEntryRepository.deleteById(waitingEntry.getId());
        userRepository.deleteById(waitingUser.getId());
    }

    private Void cancelAndReturnNull() {
        registrationService.cancelRegistration(user.getId(), registration.getId());
        return null;
    }
}

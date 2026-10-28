package com.team.eventregistration.service;

import com.team.eventregistration.PostgresTestSupport;
import com.team.eventregistration.entity.*;
import com.team.eventregistration.repository.EventRepository;
import com.team.eventregistration.repository.RegistrationRepository;
import com.team.eventregistration.repository.UserRepository;
import com.team.eventregistration.service.port.EventOfferLifecycle;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class RegistrationCancellationAtomicityIntegrationTest extends PostgresTestSupport {
    @Autowired private RegistrationService registrationService;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;
    @MockitoBean private EventOfferLifecycle eventOfferLifecycle;

    private User user;
    private Event event;
    private Registration registration;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(new User(
                UUID.randomUUID() + "@test.com", "Atomicity User", "hash", null, Role.USER));

        event = new Event();
        event.setTitle("Cancellation atomicity");
        event.setOrganizerName("Test Organizer");
        event.setCity(City.HANOI);
        event.setCategory(Category.TECHNOLOGY);
        event.setStartTime(LocalDateTime.now().plusDays(1));
        event.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
        event.setCapacity(1);
        event.setRegisteredCount(1);
        event.setStatus(EventStatus.OPEN);
        event = eventRepository.saveAndFlush(event);

        registration = registrationRepository.saveAndFlush(new Registration(user, event));
        doThrow(new IllegalStateException("allocation failed"))
                .when(eventOfferLifecycle).allocateAvailable(any(Long.class), any(LocalDateTime.class));
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
    void rollsBackCancellationWhenOfferAllocationFails() {
        assertThrows(IllegalStateException.class,
                () -> registrationService.cancelRegistration(user.getId(), registration.getId()));

        entityManager.clear();
        assertEquals(RegistrationStatus.CONFIRMED,
                registrationRepository.findById(registration.getId()).orElseThrow().getStatus());
        assertEquals(1,
                eventRepository.findById(event.getId()).orElseThrow().getRegisteredCount());
    }
}

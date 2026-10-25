package com.team.eventregistration.service;

import com.team.eventregistration.entity.*;
import com.team.eventregistration.repository.EventRepository;
import com.team.eventregistration.repository.RegistrationRepository;
import com.team.eventregistration.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class RegistrationServiceIntegrationTest {
    @Autowired private RegistrationService registrationService;

    @Autowired private EventRepository eventRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private EntityManager entityManager;

    @MockitoBean private RegistrationRepository registrationRepository;

    private User sampleUser;
    private Event sampleEvent;

    @BeforeEach
    void setUp() {
        sampleUser = new User(
                "w214-" + UUID.randomUUID() + "@test.com",
                "W2-14 Test User",
                "test-password",
                "0900000000",
                Role.USER
        );

        sampleUser = userRepository.saveAndFlush(sampleUser);

        sampleEvent = new Event();
        sampleEvent.setTitle("W2-14 Integration Test");
        sampleEvent.setOrganizerName("Test Organizer");
        sampleEvent.setCity(City.HANOI);
        sampleEvent.setCategory(Category.TECHNOLOGY);
        sampleEvent.setStartTime(LocalDateTime.now().plusDays(7));
        sampleEvent.setEndTime(LocalDateTime.now().plusDays(7).plusHours(2));
        sampleEvent.setCapacity(10);
        sampleEvent.setRegisteredCount(0);
        sampleEvent.setStatus(EventStatus.OPEN);

        sampleEvent = eventRepository.saveAndFlush(sampleEvent);
    }

    @AfterEach
    void tearDown() {
        entityManager.clear();

        eventRepository.deleteById(sampleEvent.getId());
        userRepository.deleteById(sampleUser.getId());
    }

    @Test
    @DisplayName("Rollbacks registered count when saving registration fails")
    void shouldRollbackRegisteredCountWhenSavingRegistrationFails() {
        Long userId = sampleUser.getId();
        Long eventId = sampleEvent.getId();

        when(registrationRepository.findByUser_IdAndEvent_Id(userId, eventId))
                .thenReturn(Optional.empty());

        doThrow(new DataIntegrityViolationException(
                "Simulated database error for testing rollback"
        )).when(registrationRepository)
                .saveAndFlush(any(Registration.class));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> registrationService.registerForEvent(userId, eventId)
        );

        entityManager.clear();

        Event eventAfterFailure = eventRepository
                .findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow();

        assertEquals(0, eventAfterFailure.getRegisteredCount());

        verify(registrationRepository).saveAndFlush(any(Registration.class));
    }
}

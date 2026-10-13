package com.team.eventregistration.service;

import com.team.eventregistration.dto.RegistrationResponse;
import com.team.eventregistration.entity.*;
import com.team.eventregistration.exception.BusinessRuleException;
import com.team.eventregistration.exception.DuplicateRegistrationException;
import com.team.eventregistration.exception.EventFullException;
import com.team.eventregistration.exception.ResourceNotFoundException;
import com.team.eventregistration.repository.EventRepository;
import com.team.eventregistration.repository.RegistrationRepository;
import com.team.eventregistration.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {
    @Mock private RegistrationRepository registrationRepository;

    @Mock private UserRepository userRepository;

    @Mock private EventRepository eventRepository;

    private RegistrationService registrationService;

    private User sampleUser;
    private Event sampleEvent;

    @BeforeEach
    void setUp() {
        registrationRepository = mock(RegistrationRepository.class);
        userRepository = mock(UserRepository.class);
        eventRepository = mock(EventRepository.class);

        registrationService = new RegistrationService(registrationRepository, eventRepository, userRepository);

        sampleUser = new User(
                "reimuhakurei@gensokyo.jp",
                "Reimu",
                "marisad1234",
                "09239023",
                Role.USER);

        sampleEvent = new Event();
        sampleEvent.setId(10L);
        sampleEvent.setTitle("Java Workshop");
        sampleEvent.setStatus(EventStatus.OPEN);
        sampleEvent.setStartTime(LocalDateTime.now().plusDays(7));
        sampleEvent.setEndTime(LocalDateTime.now().plusDays(7).plusHours(2));
    }

    /// Arrange - Act - Assert pattern will be used in the tests below

    @Nested
    @DisplayName("registerForEvent")
    class RegisterForEvent {

        @Test
        @DisplayName("Registers successfully when user and event exist, " +
                "event is open and not yet started, " +
                "event hasn't reached capacity," +
                "and user has not registered")
        void shouldRegisterSuccessfully() {
            // Arrange: Set up the test data
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(sampleUser));
            when(eventRepository.findByIdAndDeletedAtIsNull(10L))
                    .thenReturn(Optional.of(sampleEvent));
            when(registrationRepository.findByUser_IdAndEvent_Id(1L, 10L))
                    .thenReturn(Optional.empty());
            when(eventRepository.incrementRegisteredCountIfCapacityAvailable(10L))
                    .thenReturn(1);
            when(registrationRepository.saveAndFlush(any(Registration.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act: Call the method under test
            RegistrationResponse response = registrationService.registerForEvent(1L, 10L);

            // Assert: Verify the response and interactions
            assertNotNull(response);
            assertEquals(10L, response.getEventId());
            assertEquals("Java Workshop", response.getEventTitle());
            assertEquals(RegistrationStatus.CONFIRMED, response.getStatus());

            verify(eventRepository).incrementRegisteredCountIfCapacityAvailable(10L);
            verify(registrationRepository).saveAndFlush(any(Registration.class));
        }

        @Test
        @DisplayName("Throws DuplicateRegistrationException when registration is already confirmed")
        void shouldThrowDuplicateRegistrationException() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(sampleUser));
            when(eventRepository.findByIdAndDeletedAtIsNull(10L))
                    .thenReturn(Optional.of(sampleEvent));

            Registration existingRegistration = new Registration(sampleUser, sampleEvent);
            when(registrationRepository.findByUser_IdAndEvent_Id(1L, 10L))
                    .thenReturn(Optional.of(existingRegistration));

            assertThrows(
                    DuplicateRegistrationException.class,
                    () -> registrationService.registerForEvent(1L, 10L)
            );

            verify(registrationRepository, never()).saveAndFlush(any(Registration.class));
        }

        @Test
        @DisplayName("Allows re-registration when previous registration is canceled")
        void shouldAllowReRegistration() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(sampleUser));
            when(eventRepository.findByIdAndDeletedAtIsNull(10L))
                    .thenReturn(Optional.of(sampleEvent));

            Registration canceledRegistration = new Registration(sampleUser, sampleEvent);
            canceledRegistration.cancel();

            assertEquals(RegistrationStatus.CANCELLED, canceledRegistration.getStatus());

            when(registrationRepository.findByUser_IdAndEvent_Id(1L, 10L))
                    .thenReturn(Optional.of(canceledRegistration));
            when(eventRepository.incrementRegisteredCountIfCapacityAvailable(10L))
                    .thenReturn(1);
            when(registrationRepository.save(canceledRegistration))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            RegistrationResponse response = registrationService.registerForEvent(1L, 10L);

            assertNotNull(response);
            assertEquals(10L, response.getEventId());
            assertEquals("Java Workshop", response.getEventTitle());
            assertEquals(RegistrationStatus.CONFIRMED, response.getStatus());
            verify(eventRepository).incrementRegisteredCountIfCapacityAvailable(10L);

            ArgumentCaptor<Registration> captor = ArgumentCaptor.forClass(Registration.class);

            verify(registrationRepository).save(captor.capture());

            assertEquals(RegistrationStatus.CONFIRMED, captor.getValue().getStatus());
        }

        @Test
        @DisplayName("Propagates exception when saving registration fails")
        void shouldFailWhenSavingRegistrationFails() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(sampleUser));
            when(eventRepository.findByIdAndDeletedAtIsNull(10L))
                    .thenReturn(Optional.of(sampleEvent));
            when(registrationRepository.findByUser_IdAndEvent_Id(1L, 10L))
                    .thenReturn(Optional.empty());
            when(eventRepository.incrementRegisteredCountIfCapacityAvailable(10L))
                    .thenReturn(1);
            when(registrationRepository.saveAndFlush(any(Registration.class)))
                    .thenThrow(new DataIntegrityViolationException("Simulated database error"));

            assertThrows(
                    DataIntegrityViolationException.class,
                    () -> registrationService.registerForEvent(1L, 10L)
            );

            verify(eventRepository).incrementRegisteredCountIfCapacityAvailable(10L);
            verify(registrationRepository).saveAndFlush(any(Registration.class));
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when user does not exist")
        void shouldThrowWhenUserDoesNotExist() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.empty());

            assertThrows(
                    ResourceNotFoundException.class,
                    () -> registrationService.registerForEvent(1L, 10L)
            );

            verify(eventRepository, never()).findByIdAndDeletedAtIsNull(10L);
            verify(registrationRepository, never()).findByUser_IdAndEvent_Id(1L, 10L);
            verify(registrationRepository, never()).saveAndFlush(any(Registration.class));
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when event does not exist")
        void shouldThrowWhenEventDoesNotExist() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(sampleUser));
            when(eventRepository.findByIdAndDeletedAtIsNull(10L))
                    .thenReturn(Optional.empty());

            assertThrows(
                    ResourceNotFoundException.class,
                    () -> registrationService.registerForEvent(1L, 10L)
            );

            verify(registrationRepository, never()).findByUser_IdAndEvent_Id(1L, 10L);
            verify(registrationRepository, never()).saveAndFlush(any(Registration.class));
        }

        @Test
        @DisplayName("Throws BusinessRuleException when event is closed")
        void shouldThrowWhenEventIsClosed() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(sampleUser));

            sampleEvent.setStatus(EventStatus.CLOSED);
            when(eventRepository.findByIdAndDeletedAtIsNull(10L))
                    .thenReturn(Optional.of(sampleEvent));

            assertThrows(
                    BusinessRuleException.class,
                    () -> registrationService.registerForEvent(1L, 10L)
            );

            verify(registrationRepository, never()).saveAndFlush(any(Registration.class));
        }

        @Test
        @DisplayName("Throws BusinessRuleException when event has already started")
        void shouldThrowWhenEventHasAlreadyStarted() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(sampleUser));

            sampleEvent.setStartTime(LocalDateTime.now().minusMinutes(1));
            when(eventRepository.findByIdAndDeletedAtIsNull(10L))
                    .thenReturn(Optional.of(sampleEvent));

            assertThrows(
                    BusinessRuleException.class,
                    () -> registrationService.registerForEvent(1L, 10L)
            );

            verify(registrationRepository, never()).saveAndFlush(any(Registration.class));
        }

        @Test
        @DisplayName("Throws EventFullException when event is full")
        void shouldThrowWhenEventIsFull() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(sampleUser));
            when(eventRepository.findByIdAndDeletedAtIsNull(10L))
                    .thenReturn(Optional.of(sampleEvent));
            when(registrationRepository.findByUser_IdAndEvent_Id(1L, 10L))
                    .thenReturn(Optional.empty());
            when(eventRepository.incrementRegisteredCountIfCapacityAvailable(10L))
                    .thenReturn(0); // Simulate event is full

            assertThrows(
                    EventFullException.class,
                    () -> registrationService.registerForEvent(1L, 10L)
            );

            verify(eventRepository).incrementRegisteredCountIfCapacityAvailable(10L);
            verify(registrationRepository, never()).saveAndFlush(any(Registration.class));
        }

        @Test
        @DisplayName("Throws EventFullException when re-registering for a canceled registration and event is full")
        void shouldThrowWhenReRegisteringAndEventIsFull() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(sampleUser));
            when(eventRepository.findByIdAndDeletedAtIsNull(10L))
                    .thenReturn(Optional.of(sampleEvent));

            Registration canceledRegistration = new Registration(sampleUser, sampleEvent);
            canceledRegistration.cancel();

            when(registrationRepository.findByUser_IdAndEvent_Id(1L, 10L))
                    .thenReturn(Optional.of(canceledRegistration));
            when(eventRepository.incrementRegisteredCountIfCapacityAvailable(10L))
                    .thenReturn(0); // Simulate event is full

            assertThrows(
                    EventFullException.class,
                    () -> registrationService.registerForEvent(1L, 10L)
            );

            verify(eventRepository).incrementRegisteredCountIfCapacityAvailable(10L);
            verify(registrationRepository, never()).save(any(Registration.class));

            assertEquals(
                    RegistrationStatus.CANCELLED,
                    canceledRegistration.getStatus()
            );
        }
    }
}

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
import com.team.eventregistration.service.port.EventOfferLifecycle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {
    @Mock private RegistrationRepository registrationRepository;
    @Mock private UserRepository userRepository;
    @Mock private EventRepository eventRepository;
    @Mock private EventOfferLifecycle eventOfferLifecycle;
    @Mock private AuditLogService auditLogService;
    @Mock private RegistrationTransactionService registrationTransactionService;

    private Clock clock;

    private RegistrationService registrationService;

    private User sampleUser;
    private Event sampleEvent;

    @BeforeEach
    void setUp() {
        Instant fixedInstant = Instant.parse("2026-10-10T10:00:00Z");
        ZoneId zoneId = ZoneId.of("Asia/Ho_Chi_Minh");
        clock = Clock.fixed(fixedInstant, zoneId);

        registrationService = new RegistrationService(
                registrationRepository,
                eventRepository,
                userRepository,
                eventOfferLifecycle,
                auditLogService,
                registrationTransactionService,
                clock
        );

        sampleUser = new User(
                "reimuhakurei@gensokyo.jp",
                "Reimu",
                "marisad1234",
                "09239023",
                Role.USER);
        sampleUser.setId(1L);

        sampleEvent = new Event();
        sampleEvent.setId(10L);
        sampleEvent.setTitle("Java Workshop");
        sampleEvent.setStatus(EventStatus.OPEN);
        sampleEvent.setStartTime(LocalDateTime.now(clock).plusDays(7));
        sampleEvent.setEndTime(LocalDateTime.now(clock).plusDays(7).plusHours(2));
        sampleEvent.setCapacity(50);
    }

    @Nested
    @DisplayName("registerForEvent")
    class RegisterForEvent {

        @Test
        @DisplayName("Registers successfully when user and event exist, " +
                "event is open and not yet started, " +
                "event hasn't reached capacity," +
                "and user has not registered")
        void shouldRegisterSuccessfully() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(sampleUser));
            when(eventRepository.findById(10L))
                    .thenReturn(Optional.of(sampleEvent));
            RegistrationResponse expected = new RegistrationResponse();
            expected.setEventId(10L);
            expected.setEventTitle("Java Workshop");
            expected.setStatus(RegistrationStatus.CONFIRMED);
            when(registrationTransactionService.registerForEvent(1L, 10L)).thenReturn(expected);

            RegistrationResponse response = registrationService.registerForEvent(1L, 10L);

            assertEquals(expected, response);
            assertEquals(10L, response.getEventId());
            assertEquals("Java Workshop", response.getEventTitle());
            assertEquals(RegistrationStatus.CONFIRMED, response.getStatus());

            verify(eventOfferLifecycle).allocateAvailableForRegistration(10L);
            verify(registrationTransactionService).registerForEvent(1L, 10L);
        }

        @Test
        @DisplayName("Throws DuplicateRegistrationException when registration is already confirmed")
        void shouldThrowDuplicateRegistrationException() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(sampleUser));
            when(eventRepository.findById(10L))
                    .thenReturn(Optional.of(sampleEvent));
            when(registrationTransactionService.registerForEvent(1L, 10L))
                    .thenThrow(new DuplicateRegistrationException("duplicate"));

            assertThrows(
                    DuplicateRegistrationException.class,
                    () -> registrationService.registerForEvent(1L, 10L)
            );

            verify(eventOfferLifecycle).allocateAvailableForRegistration(10L);
        }

        @Test
        @DisplayName("Allows re-registration when previous registration is canceled")
        void shouldAllowReRegistration() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(sampleUser));
            when(eventRepository.findById(10L))
                    .thenReturn(Optional.of(sampleEvent));
            RegistrationResponse expected = new RegistrationResponse();
            expected.setEventId(10L);
            expected.setEventTitle("Java Workshop");
            expected.setStatus(RegistrationStatus.CONFIRMED);
            when(registrationTransactionService.registerForEvent(1L, 10L)).thenReturn(expected);

            RegistrationResponse response = registrationService.registerForEvent(1L, 10L);

            assertEquals(10L, response.getEventId());
            assertEquals("Java Workshop", response.getEventTitle());
            assertEquals(RegistrationStatus.CONFIRMED, response.getStatus());
            verify(eventOfferLifecycle).allocateAvailableForRegistration(10L);
            verify(registrationTransactionService).registerForEvent(1L, 10L);
        }

        @Test
        @DisplayName("Propagates exception when saving registration fails")
        void shouldFailWhenSavingRegistrationFails() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(sampleUser));
            when(eventRepository.findById(10L))
                    .thenReturn(Optional.of(sampleEvent));
            when(registrationTransactionService.registerForEvent(1L, 10L))
                    .thenThrow(new DataIntegrityViolationException("Simulated database error"));

            assertThrows(
                    DataIntegrityViolationException.class,
                    () -> registrationService.registerForEvent(1L, 10L)
            );

            verify(registrationTransactionService).registerForEvent(1L, 10L);
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

            verify(eventRepository, never()).findById(10L);
            verify(registrationRepository, never()).findByUser_IdAndEvent_Id(1L, 10L);
            verify(registrationRepository, never()).saveAndFlush(any(Registration.class));
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when event does not exist")
        void shouldThrowWhenEventDoesNotExist() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(sampleUser));
            when(eventRepository.findById(10L))
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
            when(eventRepository.findById(10L))
                    .thenReturn(Optional.of(sampleEvent));

            assertThrows(
                    BusinessRuleException.class,
                    () -> registrationService.registerForEvent(1L, 10L)
            );

            verify(registrationRepository, never()).saveAndFlush(any(Registration.class));
            verify(eventOfferLifecycle, never()).allocateAvailableForRegistration(anyLong());
            verifyNoInteractions(registrationTransactionService);
        }

        @Test
        @DisplayName("Rejects the event owner without triggering offer allocation")
        void shouldRejectEventOwnerBeforeAllocation() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            sampleEvent.setOrganizerUserId(1L);
            when(eventRepository.findById(10L)).thenReturn(Optional.of(sampleEvent));

            assertThrows(
                    BusinessRuleException.class,
                    () -> registrationService.registerForEvent(1L, 10L)
            );

            verify(eventOfferLifecycle, never())
                    .allocateAvailableForRegistration(anyLong());
            verifyNoInteractions(registrationTransactionService);
        }

        @Test
        @DisplayName("Throws BusinessRuleException when event has already started")
        void shouldThrowWhenEventHasAlreadyStarted() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(sampleUser));

            sampleEvent.setStartTime(LocalDateTime.now(clock).minusMinutes(1));
            when(eventRepository.findById(10L))
                    .thenReturn(Optional.of(sampleEvent));

            assertThrows(
                    BusinessRuleException.class,
                    () -> registrationService.registerForEvent(1L, 10L)
            );

            verify(registrationRepository, never()).saveAndFlush(any(Registration.class));
            verify(eventOfferLifecycle, never()).allocateAvailableForRegistration(anyLong());
            verifyNoInteractions(registrationTransactionService);
        }

        @Test
        @DisplayName("Throws EventFullException when event is full")
        void shouldThrowWhenEventIsFull() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(sampleUser));
            when(eventRepository.findById(10L))
                    .thenReturn(Optional.of(sampleEvent));
            when(registrationTransactionService.registerForEvent(1L, 10L))
                    .thenThrow(new EventFullException("full"));

            assertThrows(
                    EventFullException.class,
                    () -> registrationService.registerForEvent(1L, 10L)
            );

            verify(registrationTransactionService).registerForEvent(1L, 10L);
        }

        @Test
        @DisplayName("Throws EventFullException when re-registering for a canceled registration and event is full")
        void shouldThrowWhenReRegisteringAndEventIsFull() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(sampleUser));
            when(eventRepository.findById(10L))
                    .thenReturn(Optional.of(sampleEvent));

            when(registrationTransactionService.registerForEvent(1L, 10L))
                    .thenThrow(new EventFullException("full"));

            assertThrows(
                    EventFullException.class,
                    () -> registrationService.registerForEvent(1L, 10L)
            );

            verify(eventOfferLifecycle).allocateAvailableForRegistration(10L);
            verify(registrationTransactionService).registerForEvent(1L, 10L);
        }
    }

    @Nested
    @DisplayName("getMyRegistrations")
    class GetMyRegistrations {

        @Test
        @DisplayName("Returns the user's registrations mapped to responses")
        void shouldReturnUserRegistrations() {
            Registration firstRegistration = new Registration(sampleUser, sampleEvent);
            Registration secondRegistration = new Registration(sampleUser, sampleEvent);

            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(sampleUser));
            when(registrationRepository.findByUser_Id(1L))
                    .thenReturn(List.of(firstRegistration, secondRegistration));

            List<RegistrationResponse> responses =
                    registrationService.getMyRegistrations(1L);

            assertEquals(2, responses.size());

            assertEquals(RegistrationStatus.CONFIRMED, responses.get(0).getStatus());
            assertEquals(10L, responses.get(0).getEventId());
            assertEquals("Java Workshop", responses.get(0).getEventTitle());

            assertEquals(RegistrationStatus.CONFIRMED, responses.get(1).getStatus());
            assertEquals(10L, responses.get(1).getEventId());
            assertEquals("Java Workshop", responses.get(1).getEventTitle());

            verify(userRepository).findById(1L);
            verify(registrationRepository).findByUser_Id(1L);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when user does not exist")
        void shouldThrowWhenUserDoesNotExist() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.empty());

            assertThrows(
                    ResourceNotFoundException.class,
                    () -> registrationService.getMyRegistrations(1L)
            );

            verify(registrationRepository, never()).findByUser_Id(anyLong());
        }

        @Test
        @DisplayName("Returns an empty list when user has no registrations")
        void shouldReturnEmptyListWhenNoRegistrations() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(sampleUser));
            when(registrationRepository.findByUser_Id(1L))
                    .thenReturn(List.of());

            List<RegistrationResponse> responses =
                    registrationService.getMyRegistrations(1L);

            assertTrue(responses.isEmpty());

            verify(userRepository).findById(1L);
            verify(registrationRepository).findByUser_Id(1L);
        }
    }

    @Nested
    @DisplayName("cancelRegistration")
    class CancelRegistration {

        @Test
        @DisplayName("Cancels a confirmed registration and releases its event slot")
        void shouldCancelRegistration() {
            Registration registration = new Registration(sampleUser, sampleEvent);
            lenient().when(registrationRepository.findEventIdForUser(20L, 1L)).thenReturn(Optional.of(10L));
            when(registrationRepository.findById(20L)).thenReturn(Optional.of(registration));
            when(eventRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleEvent));
            when(registrationRepository.cancelConfirmedRegistration(eq(20L), eq(1L), any(LocalDateTime.class))).thenReturn(1);
            when(eventRepository.decrementRegisteredCount(10L)).thenReturn(1);

            registrationService.cancelRegistration(1L, 20L);

            verify(eventRepository).findByIdForUpdate(10L);
            verify(registrationRepository).cancelConfirmedRegistration(eq(20L), eq(1L), any(LocalDateTime.class));
            verify(eventRepository).decrementRegisteredCount(10L);
        }

        @Test
        @DisplayName("Does not release a slot when registration is already cancelled")
        void shouldBeIdempotentForCancelledRegistration() {
            Registration registration = new Registration(sampleUser, sampleEvent);
            registration.cancel();
            lenient().when(registrationRepository.findEventIdForUser(20L, 1L)).thenReturn(Optional.of(10L));
            when(registrationRepository.findById(20L)).thenReturn(Optional.of(registration));
            when(eventRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleEvent));

            registrationService.cancelRegistration(1L, 20L);

            assertEquals(RegistrationStatus.CANCELLED, registration.getStatus());
            verify(eventRepository, never()).decrementRegisteredCount(anyLong());
            verify(registrationRepository, never()).cancelConfirmedRegistration(anyLong(), anyLong(), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when registration does not belong to user")
        void shouldNotCancelAnotherUsersRegistration() {
            User anotherUser = new User(
                    "another@example.com", "Another User", "password", "0912345678", Role.USER);
            Registration registration = new Registration(anotherUser, sampleEvent);
            when(registrationRepository.findEventIdForUser(20L, 1L)).thenReturn(Optional.empty());

            assertThrows(
                    ResourceNotFoundException.class,
                    () -> registrationService.cancelRegistration(1L, 20L)
            );

            verify(eventRepository, never()).findByIdForUpdate(anyLong());
            verify(eventRepository, never()).decrementRegisteredCount(anyLong());
            verify(registrationRepository, never()).cancelConfirmedRegistration(anyLong(), anyLong(), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when registration does not exist")
        void shouldThrowWhenRegistrationDoesNotExist() {
            when(registrationRepository.findEventIdForUser(20L, 1L)).thenReturn(Optional.empty());

            assertThrows(
                    ResourceNotFoundException.class,
                    () -> registrationService.cancelRegistration(1L, 20L)
            );

            verify(eventRepository, never()).findByIdForUpdate(anyLong());
            verify(eventRepository, never()).decrementRegisteredCount(anyLong());
            verify(registrationRepository, never()).cancelConfirmedRegistration(anyLong(), anyLong(), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("Rejects cancellation after the event has started")
        void shouldRejectCancellationAfterEventStarts() {
            Registration registration = new Registration(sampleUser, sampleEvent);
            sampleEvent.setStartTime(LocalDateTime.now(clock).minusMinutes(1));
            lenient().when(registrationRepository.findEventIdForUser(20L, 1L)).thenReturn(Optional.of(10L));
            when(registrationRepository.findById(20L)).thenReturn(Optional.of(registration));
            when(eventRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleEvent));

            assertThrows(
                    BusinessRuleException.class,
                    () -> registrationService.cancelRegistration(1L, 20L)
            );

            verify(registrationRepository, never()).cancelConfirmedRegistration(anyLong(), anyLong(), any(LocalDateTime.class));
            verify(eventRepository, never()).decrementRegisteredCount(anyLong());
        }

        @Test
        @DisplayName("Rolls back when the event counter cannot be decremented")
        void shouldFailWhenCounterUpdateDoesNotAffectOneRow() {
            Registration registration = new Registration(sampleUser, sampleEvent);
            lenient().when(registrationRepository.findEventIdForUser(20L, 1L)).thenReturn(Optional.of(10L));
            when(registrationRepository.findById(20L)).thenReturn(Optional.of(registration));
            when(eventRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleEvent));
            when(registrationRepository.cancelConfirmedRegistration(eq(20L), eq(1L), any(LocalDateTime.class))).thenReturn(1);
            when(eventRepository.decrementRegisteredCount(10L)).thenReturn(0);

            assertThrows(
                    BusinessRuleException.class,
                    () -> registrationService.cancelRegistration(1L, 20L)
            );

            verify(registrationRepository).cancelConfirmedRegistration(eq(20L), eq(1L), any(LocalDateTime.class));
            verify(eventRepository).decrementRegisteredCount(10L);
        }

        @Test
        @DisplayName("Rejects an unexpected registration state")
        void shouldRejectUnexpectedRegistrationState() {
            Registration registration = mock(Registration.class);
            when(registration.getUser()).thenReturn(sampleUser);
            when(registration.getStatus()).thenReturn(null);
            lenient().when(registrationRepository.findEventIdForUser(20L, 1L)).thenReturn(Optional.of(10L));
            when(registrationRepository.findById(20L)).thenReturn(Optional.of(registration));
            when(eventRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(sampleEvent));

            assertThrows(
                    BusinessRuleException.class,
                    () -> registrationService.cancelRegistration(1L, 20L)
            );

            verify(registrationRepository, never()).cancelConfirmedRegistration(anyLong(), anyLong(), any(LocalDateTime.class));
            verify(eventRepository, never()).decrementRegisteredCount(anyLong());
        }
    }
}

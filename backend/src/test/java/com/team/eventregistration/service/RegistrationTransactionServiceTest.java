package com.team.eventregistration.service;

import com.team.eventregistration.dto.RegistrationResponse;
import com.team.eventregistration.entity.*;
import com.team.eventregistration.exception.BusinessRuleException;
import com.team.eventregistration.exception.DuplicateRegistrationException;
import com.team.eventregistration.exception.EventFullException;
import com.team.eventregistration.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationTransactionServiceTest {
    @Mock private RegistrationRepository registrationRepository;
    @Mock private EventRepository eventRepository;
    @Mock private UserRepository userRepository;
    @Mock private WaitlistEntryRepository waitlistEntryRepository;
    @Mock private SlotOfferRepository slotOfferRepository;
    @Mock private AuditLogService auditLogService;

    private RegistrationTransactionService service;
    private User user;
    private Event event;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-10-10T03:00:00Z"),
                ZoneId.of("Asia/Ho_Chi_Minh"));
        service = new RegistrationTransactionService(
                registrationRepository, eventRepository, userRepository,
                waitlistEntryRepository, slotOfferRepository,
                auditLogService, clock);

        user = new User("attendee@example.com", "Attendee", "hash", null, Role.USER);
        user.setId(1L);
        event = new Event();
        event.setId(10L);
        event.setTitle("Workshop");
        event.setOrganizerName("Organizer");
        event.setOrganizerUserId(99L);
        event.setStatus(EventStatus.OPEN);
        event.setStartTime(LocalDateTime.of(2026, 10, 11, 10, 0));
        event.setCapacity(1);
        event.setRegisteredCount(0);
        event.setHeldCount(0);
    }

    @Test
    void registersWhenCapacityIsAvailable() {
        givenValidEvent();
        when(registrationRepository.findByUser_IdAndEvent_Id(1L, 10L)).thenReturn(Optional.empty());
        when(eventRepository.incrementRegisteredCountIfCapacityAvailable(10L)).thenReturn(1);
        when(registrationRepository.saveAndFlush(any(Registration.class)))
                .thenAnswer(invocation -> {
                    Registration registration = invocation.getArgument(0);
                    Field idField = Registration.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(registration, 100L);
                    return registration;
                });

        RegistrationResponse response = service.registerForEvent(1L, 10L);

        assertEquals(RegistrationStatus.CONFIRMED, response.getStatus());
        assertEquals(10L, response.getEventId());
        verify(eventRepository).incrementRegisteredCountIfCapacityAvailable(10L);
        verify(registrationRepository).saveAndFlush(any(Registration.class));
    }

    @Test
    void rejectsRegistrationWhenCapacityWasAllocatedToWaitingUser() {
        event.setHeldCount(1);
        givenValidEvent();

        assertThrows(EventFullException.class, () -> service.registerForEvent(1L, 10L));

        verify(registrationRepository, never()).findByUser_IdAndEvent_Id(anyLong(), anyLong());
        verify(eventRepository, never()).incrementRegisteredCountIfCapacityAvailable(anyLong());
        verify(registrationRepository, never()).saveAndFlush(any(Registration.class));
    }

    @Test
    void leavesOfferStateUntouchedWhenRegistrationPersistenceFails() {
        givenValidEvent();
        when(registrationRepository.findByUser_IdAndEvent_Id(1L, 10L)).thenReturn(Optional.empty());
        when(eventRepository.incrementRegisteredCountIfCapacityAvailable(10L)).thenReturn(1);
        when(registrationRepository.saveAndFlush(any(Registration.class)))
                .thenThrow(new IllegalStateException("registration insert failed"));

        assertThrows(IllegalStateException.class, () -> service.registerForEvent(1L, 10L));

        assertEquals(0, event.getHeldCount());
        verify(auditLogService, never()).recordEventAction(
                anyLong(), anyLong(), anyString(), anyString(), anyMap());
    }

    @Test
    void rejectsOwnerBeforeCheckingCapacityOrRegistration() {
        event.setOrganizerUserId(1L);
        givenValidEvent();

        assertThrows(BusinessRuleException.class, () -> service.registerForEvent(1L, 10L));

        verify(eventRepository, never()).incrementRegisteredCountIfCapacityAvailable(anyLong());
        verify(registrationRepository, never()).saveAndFlush(any(Registration.class));
    }

    @Test
    void rejectsClosedAndStartedEventsBeforeCheckingCapacity() {
        givenValidEvent();
        event.setStatus(EventStatus.CLOSED);
        assertThrows(BusinessRuleException.class, () -> service.registerForEvent(1L, 10L));

        event.setStatus(EventStatus.OPEN);
        event.setStartTime(LocalDateTime.of(2026, 10, 10, 10, 0));
        assertThrows(BusinessRuleException.class, () -> service.registerForEvent(1L, 10L));

        verify(eventRepository, never()).incrementRegisteredCountIfCapacityAvailable(anyLong());
        verify(registrationRepository, never()).saveAndFlush(any(Registration.class));
    }

    @Test
    void rejectsDuplicateRegistrationWithoutChangingCapacity() {
        givenValidEvent();
        Registration existing = new Registration(user, event);
        when(registrationRepository.findByUser_IdAndEvent_Id(1L, 10L)).thenReturn(Optional.of(existing));

        assertThrows(DuplicateRegistrationException.class, () -> service.registerForEvent(1L, 10L));

        verify(eventRepository, never()).incrementRegisteredCountIfCapacityAvailable(anyLong());
        verify(registrationRepository, never()).saveAndFlush(any(Registration.class));
    }

    private void givenValidEvent() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(event));
    }
}

package com.team.eventregistration.service;

import com.team.eventregistration.dto.RegistrationHistoryResponse;
import com.team.eventregistration.dto.RegistrationResponse;
import com.team.eventregistration.entity.*;
import com.team.eventregistration.exception.BusinessRuleException;
import com.team.eventregistration.exception.ResourceNotFoundException;
import com.team.eventregistration.repository.EventRepository;
import com.team.eventregistration.repository.RegistrationRepository;
import com.team.eventregistration.repository.UserRepository;
import com.team.eventregistration.service.port.EventOfferLifecycle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class RegistrationService {
    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final EventOfferLifecycle eventOfferLifecycle;
    private final AuditLogService auditLogService;
    private final RegistrationTransactionService registrationTransactionService;
    private final Clock clock;

    public RegistrationService(
            RegistrationRepository registrationRepository,
            EventRepository eventRepository,
            UserRepository userRepository,
            EventOfferLifecycle eventOfferLifecycle,
            AuditLogService auditLogService,
            RegistrationTransactionService registrationTransactionService,
            Clock clock
    ) {
        this.registrationRepository = registrationRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.eventOfferLifecycle = eventOfferLifecycle;
        this.auditLogService = auditLogService;
        this.registrationTransactionService = registrationTransactionService;
        this.clock = clock;
    }

    public RegistrationResponse registerForEvent(Long userId, Long eventId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        Event event = eventRepository.findById(eventId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));
        LocalDateTime now = LocalDateTime.now(clock);

        if (Objects.equals(event.getOrganizerUserId(), userId)) {
            throw new BusinessRuleException("Event owners cannot register for their own events.");
        }
        if (event.getStatus() != EventStatus.OPEN) {
            throw new BusinessRuleException("Cannot register for a closed event.");
        }
        if (!now.isBefore(event.getStartTime())) {
            throw new BusinessRuleException("Cannot register for an event that has already started.");
        }

        eventOfferLifecycle.allocateAvailableForRegistration(eventId);

        return registrationTransactionService.registerForEvent(userId, eventId);
    }

    @Transactional(readOnly = true)
    public List<RegistrationResponse> getMyRegistrations(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        List<Registration> registrations = registrationRepository.findByUser_Id(userId);
        Map<Long, List<RegistrationHistoryResponse>> history = auditLogService.registrationHistory(userId);
        return registrations.stream()
                .map(registration -> {
                    RegistrationResponse response = mapToResponse(registration);
                    response.setHistory(history.getOrDefault(registration.getId(), List.of()));
                    return response;
                })
                .toList();
    }

    @Transactional
    public void cancelRegistration(Long userId, Long registrationId) {
        Long eventId = registrationRepository.findEventIdForUser(registrationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found with ID: " + registrationId));

        Event event = eventRepository.findByIdForUpdate(eventId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        LocalDateTime now = LocalDateTime.now(clock);

        Registration registration = registrationRepository.findById(registrationId)
                .filter(candidate -> Objects.equals(candidate.getUser().getId(), userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Registration not found with ID: " + registrationId));

        if (registration.getStatus() == RegistrationStatus.CANCELLED) {
            return;
        }

        if (registration.getStatus() != RegistrationStatus.CONFIRMED) {
            throw new BusinessRuleException(
                    "Registration cannot be cancelled in its current state.");
        }

        if (!now.isBefore(event.getStartTime())) {
            throw new BusinessRuleException(
                    "Cannot cancel a registration after the event has started.");
        }

        int registrationRows = registrationRepository.cancelConfirmedRegistration(
                registrationId, userId, now);

        if (registrationRows == 0) {
            Optional<RegistrationStatus> currentStatus =
                    registrationRepository.findStatusById(registrationId);

            if (currentStatus.isPresent()
                    && currentStatus.get() == RegistrationStatus.CANCELLED) {
                return;
            }

            throw new BusinessRuleException(
                    "Registration cannot be cancelled in its current state.");
        }

        if (registrationRows != 1) {
            throw new BusinessRuleException(
                    "Registration cancellation could not be completed consistently.");
        }

        int counterRows = eventRepository.decrementRegisteredCount(eventId);
        if (counterRows != 1) {
            throw new BusinessRuleException(
                    "Event registration count could not be updated consistently.");
        }

        event.setRegisteredCount(event.getRegisteredCount() - 1);

        Map<String, Object> details = Map.of(
                "previous_status", RegistrationStatus.CONFIRMED.name(),
                "new_status", RegistrationStatus.CANCELLED.name(),
                "registration_id", registrationId,
                "user_id", userId,
                "cancelled_at", now.toString()
        );

        auditLogService.recordEventAction(
                eventId,
                userId,
                "REGISTRATION_CANCEL",
                "User cancelled their registration for the event.",
                details
        );

        eventOfferLifecycle.allocateAvailable(eventId, now);
    }

    private RegistrationResponse mapToResponse(Registration registration) {
        RegistrationResponse response = new RegistrationResponse();
        response.setId(registration.getId());
        response.setStatus(registration.getStatus());
        response.setEventId(registration.getEvent().getId());
        response.setEventTitle(registration.getEvent().getTitle());
        response.setRegisteredAt(registration.getRegisteredAt());
        return response;
    }
}

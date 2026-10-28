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
    private final Clock clock;

    public RegistrationService(
            RegistrationRepository registrationRepository,
            EventRepository eventRepository,
            UserRepository userRepository,
            EventOfferLifecycle eventOfferLifecycle,
            AuditLogService auditLogService,
            Clock clock
    ) {
        this.registrationRepository = registrationRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.eventOfferLifecycle = eventOfferLifecycle;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional
    public RegistrationResponse registerForEvent(Long userId, Long eventId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        Event event = eventRepository.findByIdForUpdate(eventId)
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

        eventOfferLifecycle.allocateAvailable(event, now);

        int unreservedSlots = event.getCapacity() - event.getRegisteredCount() - event.getHeldCount();
        long waitingCount = eventOfferLifecycle.waitingCount(event);
        if (unreservedSlots <= 0 && waitingCount > 0) {
            throw new EventFullException("Event is full or has a waitlist. Please join the waitlist instead.");
        }

        Registration existingRegistration = registrationRepository
                .findByUser_IdAndEvent_Id(userId, eventId)
                .orElse(null);

        Registration savedRegistration;
        String previousStatus = null;

        if (existingRegistration != null) {
            if (existingRegistration.getStatus() == RegistrationStatus.CONFIRMED) {
                throw new DuplicateRegistrationException("User has already registered for this event.");
            }
            if (existingRegistration.getStatus() == RegistrationStatus.REMOVED) {
                throw new BusinessRuleException("Removed participants cannot register again for this event.");
            }

            previousStatus = existingRegistration.getStatus().name();

            int updatedRows = eventRepository.incrementRegisteredCountIfCapacityAvailable(eventId);
            if (updatedRows == 0) {
                throw new EventFullException("Event is full. Cannot register.");
            }

            existingRegistration.confirmAgain(now);
            savedRegistration = registrationRepository.save(existingRegistration);
        } else {
            int updatedRows = eventRepository.incrementRegisteredCountIfCapacityAvailable(eventId);
            if (updatedRows == 0) {
                throw new EventFullException("Event is full. Cannot register.");
            }

            Registration registration = new Registration(user, event, now);
            try {
                savedRegistration = registrationRepository.saveAndFlush(registration);
            } catch (RuntimeException ex) {
                if (RegistrationRepository.isDuplicateUserEvent(ex)) {
                    throw new DuplicateRegistrationException("User has already registered for this event.");
                }
                throw ex;
            }
        }

        Map<String, Object> details = Map.of(
                "previous_status", previousStatus != null ? previousStatus : "NONE",
                "new_status", RegistrationStatus.CONFIRMED.name(),
                "registration_id", savedRegistration.getId(),
                "user_id", userId,
                "registered_at", now.toString()
        );

        auditLogService.recordEventAction(
                eventId,
                userId,
                "REGISTRATION_CONFIRM",
                previousStatus == null ? "Initial registration confirmed" : "Re-registration confirmed after cancellation",
                details
        );

        return mapToResponse(savedRegistration);
    }

    @Transactional(readOnly = true)
    public List<RegistrationResponse> getMyRegistrations(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        List<Registration> registrations = registrationRepository.findByUser_Id(userId);
        return registrations.stream()
                .map(this::mapToResponse)
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

        eventOfferLifecycle.allocateAvailable(event, now);
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

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
import org.springframework.beans.factory.annotation.Autowired;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class RegistrationService {
    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final SlotOfferService slotOfferService;

    @Autowired
    public RegistrationService(
            RegistrationRepository registrationRepository,
            EventRepository eventRepository,
            UserRepository userRepository,
            SlotOfferService slotOfferService
    ) {
        this.registrationRepository = registrationRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.slotOfferService = slotOfferService;
    }

    @Transactional
    public RegistrationResponse registerForEvent(Long userId, Long eventId) {
        // 1. Validate user and event existence
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        // 2. Check if the event is closed
        if (event.getStatus() != EventStatus.OPEN) {
            throw new BusinessRuleException("Cannot register for a closed event.");
        }

        // 3. Check if the event has already started
        if (!LocalDateTime.now().isBefore(event.getStartTime())) {
            throw new BusinessRuleException("Cannot register for an event that has already started.");
        }

        // 4. Check if the user has already registered for the event
        Registration existingRegistration = registrationRepository
                .findByUser_IdAndEvent_Id(userId, eventId)
                .orElse(null);

        if (existingRegistration != null) {
            if (existingRegistration.getStatus() == RegistrationStatus.CONFIRMED) {
                throw new DuplicateRegistrationException("User has already registered for this event.");
            }

            // Atomically reserve a slot before re-registering
            int updatedRows = eventRepository.incrementRegisteredCountIfCapacityAvailable(eventId);
            if (updatedRows == 0) {
                throw new EventFullException("Event is full. Cannot register.");
            }

            existingRegistration.confirmAgain();
            return mapToResponse(registrationRepository.save(existingRegistration));
        }

        // 5. Atomically reserve a slot before creating the registration
        int updatedRows = eventRepository.incrementRegisteredCountIfCapacityAvailable(eventId);
        if (updatedRows == 0) {
            throw new EventFullException("Event is full. Cannot register.");
        }

        // 6. Save the new registration and handle potential duplicate registration exceptions
        Registration registration = new Registration(user, event);
        try {
            Registration savedRegistration = registrationRepository.saveAndFlush(registration);
            return mapToResponse(savedRegistration);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateRegistration(ex)) {
                throw new DuplicateRegistrationException("User has already registered for this event.");
            }
            throw ex; // rethrow if it's not a duplicate registration
        }
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
        // 1. Find the registration and verify ownership
        Registration registration = registrationRepository.findById(registrationId)
                .filter(candidate -> Objects.equals(candidate.getUser().getId(), userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Registration not found with ID: " + registrationId));

        // 2. Lock the event to synchronize concurrent operations affecting its capacity
        Long eventId = registration.getEvent().getId();
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Event not found with ID: " + eventId));

        // 3. Return if the registration has already been cancelled
        if (registration.getStatus() == RegistrationStatus.CANCELLED) {
            return;
        }

        // 4. Ensure that the registration is in a cancellable state
        if (registration.getStatus() != RegistrationStatus.CONFIRMED) {
            throw new BusinessRuleException(
                    "Registration cannot be cancelled in its current state.");
        }

        // 5. Prevent cancellation after the event has started
        if (!LocalDateTime.now().isBefore(event.getStartTime())) {
            throw new BusinessRuleException(
                    "Cannot cancel a registration after the event has started.");
        }

        // 6. Atomically transition the registration from CONFIRMED to CANCELLED
        int registrationRows = registrationRepository.cancelConfirmedRegistration(
                registrationId, userId);

        // 7. Handle cases where the registration was changed by another operation
        if (registrationRows == 0) {
            Optional<RegistrationStatus> currentStatus =
                    registrationRepository.findStatusById(registrationId);

            // Preserve idempotent behavior if the registration was already cancelled
            if (currentStatus.isPresent()
                    && currentStatus.get() == RegistrationStatus.CANCELLED) {
                return;
            }

            throw new BusinessRuleException(
                    "Registration cannot be cancelled in its current state.");
        }

        // Ensure exactly one registration was cancelled
        if (registrationRows != 1) {
            throw new BusinessRuleException(
                    "Registration cancellation could not be completed consistently.");
        }

        // 8. Release the event slot after successfully cancelling the registration
        int counterRows = eventRepository.decrementRegisteredCount(eventId);

        // Ensure the event counter was updated successfully; otherwise, roll back the transaction
        if (counterRows != 1) {
            throw new BusinessRuleException(
                    "Event registration count could not be updated consistently.");
        }

        slotOfferService.offerNextWaitingEntry(eventId);
    }

    private boolean isDuplicateRegistration(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();

        while (cause != null) {
            if (cause instanceof ConstraintViolationException cve) {
                return "uk_registrations_user_event".equals(cve.getConstraintName());
            }

            cause = cause.getCause();
        }

        return false;
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

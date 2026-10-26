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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class RegistrationService {
    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public RegistrationService(
            RegistrationRepository registrationRepository,
            EventRepository eventRepository,
            UserRepository userRepository, Clock clock
    ) {
        this.registrationRepository = registrationRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public RegistrationResponse registerForEvent(Long userId, Long eventId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        Event event = eventRepository.findByIdForUpdate(eventId).filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        if (event.getStatus() != EventStatus.OPEN) {
            throw new BusinessRuleException("Cannot register for a closed event.");
        }

        if (!LocalDateTime.now(clock).isBefore(event.getStartTime())) {
            throw new BusinessRuleException("Cannot register for an event that has already started.");
        }

        Registration existingRegistration = registrationRepository
                .findByUser_IdAndEvent_Id(userId, eventId)
                .orElse(null);

        if (existingRegistration != null) {
            if (existingRegistration.getStatus() == RegistrationStatus.CONFIRMED) {
                throw new DuplicateRegistrationException("User has already registered for this event.");
            }

            if (existingRegistration.getStatus() == RegistrationStatus.REMOVED) {
                throw new BusinessRuleException("Removed participants cannot register again for this event.");
            }

            int updatedRows = eventRepository.incrementRegisteredCountIfCapacityAvailable(eventId);
            if (updatedRows == 0) {
                throw new EventFullException("Event is full. Cannot register.");
            }

            existingRegistration.confirmAgain(LocalDateTime.now(clock));
            return mapToResponse(registrationRepository.save(existingRegistration));
        }

        int updatedRows = eventRepository.incrementRegisteredCountIfCapacityAvailable(eventId);
        if (updatedRows == 0) {
            throw new EventFullException("Event is full. Cannot register.");
        }

        Registration registration = new Registration(user, event, LocalDateTime.now(clock));
        try {
            Registration savedRegistration = registrationRepository.saveAndFlush(registration);
            return mapToResponse(savedRegistration);
        } catch (RuntimeException ex) {
            if (RegistrationRepository.isDuplicateUserEvent(ex)) {
                throw new DuplicateRegistrationException("User has already registered for this event.");
            }
            throw ex;
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
        Long eventId = registrationRepository.findEventIdForUser(registrationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found with ID: " + registrationId));
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));
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

        if (!LocalDateTime.now(clock).isBefore(event.getStartTime())) {
            throw new BusinessRuleException(
                    "Cannot cancel a registration after the event has started.");
        }

        int registrationRows = registrationRepository.cancelConfirmedRegistration(
                registrationId, userId, LocalDateTime.now(clock));

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

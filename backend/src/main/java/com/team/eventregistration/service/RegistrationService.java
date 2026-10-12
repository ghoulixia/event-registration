package com.team.eventregistration.service;

import com.team.eventregistration.dto.RegistrationResponse;
import com.team.eventregistration.entity.*;
import com.team.eventregistration.exception.BusinessRuleException;
import com.team.eventregistration.exception.DuplicateRegistrationException;
import com.team.eventregistration.exception.ResourceNotFoundException;
import com.team.eventregistration.repository.EventRepository;
import com.team.eventregistration.repository.RegistrationRepository;
import com.team.eventregistration.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class RegistrationService {
    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    public RegistrationService(
            RegistrationRepository registrationRepository,
            EventRepository eventRepository,
            UserRepository userRepository
    ) {
        this.registrationRepository = registrationRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
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

            existingRegistration.confirmAgain();
            return mapToResponse(registrationRepository.save(existingRegistration));
        }

        // 5. Save the new registration and handle potential duplicate registration exceptions
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

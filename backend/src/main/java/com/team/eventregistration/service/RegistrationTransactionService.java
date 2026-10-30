package com.team.eventregistration.service;

import com.team.eventregistration.dto.RegistrationResponse;
import com.team.eventregistration.entity.*;
import com.team.eventregistration.exception.BusinessRuleException;
import com.team.eventregistration.exception.DuplicateRegistrationException;
import com.team.eventregistration.exception.EventFullException;
import com.team.eventregistration.exception.ResourceNotFoundException;
import com.team.eventregistration.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RegistrationTransactionService {
    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final SlotOfferRepository slotOfferRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public RegistrationTransactionService(
            RegistrationRepository registrationRepository,
            EventRepository eventRepository,
            UserRepository userRepository,
            WaitlistEntryRepository waitlistEntryRepository,
            SlotOfferRepository slotOfferRepository,
            AuditLogService auditLogService,
            Clock clock
    ) {
        this.registrationRepository = registrationRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.slotOfferRepository = slotOfferRepository;
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

        if (waitlistEntryRepository.findByUser_IdAndEvent_IdAndStatusIn(
                userId, eventId, List.of(WaitlistEntryStatus.WAITING, WaitlistEntryStatus.OFFERED)).isPresent()) {
            throw new BusinessRuleException("User is already on the waitlist for this event. Please accept or decline the waitlist offer before registering.");
        }
        if (slotOfferRepository.findByUser_IdAndEvent_IdAndStatus(
                userId, eventId, SlotOfferStatus.PENDING).isPresent()) {
            throw new BusinessRuleException("User already has a pending slot offer for this event. Please accept or decline the offer before registering.");
        }

        int unreservedSlots = event.getCapacity() - event.getRegisteredCount() - event.getHeldCount();

        if (unreservedSlots <= 0) {
            throw new EventFullException("Event is full or all available places are reserved. Please join the waitlist instead.");
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

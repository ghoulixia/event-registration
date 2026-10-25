package com.team.eventregistration.service;

import com.team.eventregistration.dto.WaitlistEntryResponse;
import com.team.eventregistration.entity.*;
import com.team.eventregistration.exception.BusinessRuleException;
import com.team.eventregistration.exception.DuplicateWaitlistEntryException;
import com.team.eventregistration.exception.ResourceNotFoundException;
import com.team.eventregistration.repository.EventRepository;
import com.team.eventregistration.repository.RegistrationRepository;
import com.team.eventregistration.repository.UserRepository;
import com.team.eventregistration.repository.WaitlistEntryRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class WaitlistService {
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final RegistrationRepository registrationRepository;

    public WaitlistService(
            WaitlistEntryRepository waitlistEntryRepository,
            EventRepository eventRepository,
            UserRepository userRepository,
            RegistrationRepository registrationRepository
    ) {
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.registrationRepository = registrationRepository;
    }

    @Transactional
    public WaitlistEntryResponse joinWaitlist(Long userId, Long eventId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        validateEventCanAcceptWaitlist(event);
        if (registrationRepository.findByUser_IdAndEvent_Id(userId, eventId)
                .filter(registration -> registration.getStatus() == RegistrationStatus.CONFIRMED)
                .isPresent()) {
            throw new BusinessRuleException("A confirmed registration already exists for this event.");
        }
        if (waitlistEntryRepository.findByUser_IdAndEvent_IdAndStatus(
                userId, eventId, WaitlistEntryStatus.WAITING).isPresent()) {
            throw new DuplicateWaitlistEntryException("User is already waiting for this event.");
        }

        try {
            WaitlistEntry entry = waitlistEntryRepository.saveAndFlush(new WaitlistEntry(user, event));
            List<WaitlistEntry> entries = waitlistEntryRepository
                    .findByEvent_IdAndStatusOrderByJoinedAtAscIdAsc(
                            eventId, WaitlistEntryStatus.WAITING);
            return mapToResponse(entry, entries.indexOf(entry) + 1);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateWaitlistEntry(ex)) {
                throw new DuplicateWaitlistEntryException("User is already waiting for this event.");
            }
            throw ex;
        }
    }

    @Transactional
    public void leaveWaitlist(Long userId, Long eventId) {
        WaitlistEntry entry = waitlistEntryRepository
                .findByUser_IdAndEvent_IdAndStatus(userId, eventId, WaitlistEntryStatus.WAITING)
                .orElseThrow(() -> new ResourceNotFoundException("Active waitlist entry not found."));
        entry.leave();
        waitlistEntryRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<WaitlistEntryResponse> getEventWaitlist(Long eventId) {
        eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));
        List<WaitlistEntry> entries = waitlistEntryRepository
                .findByEvent_IdAndStatusOrderByJoinedAtAscIdAsc(eventId, WaitlistEntryStatus.WAITING);
        return mapEntries(entries);
    }

    @Transactional(readOnly = true)
    public List<WaitlistEntryResponse> getMyWaitlist(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        return mapEntries(waitlistEntryRepository
                .findByUser_IdAndStatusOrderByJoinedAtAscIdAsc(userId, WaitlistEntryStatus.WAITING));
    }

    private void validateEventCanAcceptWaitlist(Event event) {
        if (event.getDeletedAt() != null || event.getStatus() != EventStatus.OPEN) {
            throw new BusinessRuleException("Cannot join the waitlist for a closed event.");
        }
        if (!LocalDateTime.now().isBefore(event.getStartTime())) {
            throw new BusinessRuleException("Cannot join the waitlist after the event has started.");
        }
        int heldCount = event.getHeldCount() == null ? 0 : event.getHeldCount();
        if (event.getRegisteredCount() + heldCount < event.getCapacity()) {
            throw new BusinessRuleException("The event still has available capacity.");
        }
    }

    private boolean isDuplicateWaitlistEntry(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();

        while (cause != null) {
            if (cause instanceof ConstraintViolationException cve) {
                return "uk_waitlist_active_user_event".equals(cve.getConstraintName());
            }
            cause = cause.getCause();
        }

        return false;
    }

    private List<WaitlistEntryResponse> mapEntries(List<WaitlistEntry> entries) {
        return java.util.stream.IntStream.range(0, entries.size())
                .mapToObj(index -> mapToResponse(entries.get(index), index + 1))
                .toList();
    }

    private WaitlistEntryResponse mapToResponse(WaitlistEntry entry, int position) {
        WaitlistEntryResponse response = new WaitlistEntryResponse();
        response.setId(entry.getId());
        response.setEventId(entry.getEvent().getId());
        response.setEventTitle(entry.getEvent().getTitle());
        response.setUserId(entry.getUser().getId());
        response.setStatus(entry.getStatus());
        response.setJoinedAt(entry.getJoinedAt());
        response.setPosition(position);
        return response;
    }
}

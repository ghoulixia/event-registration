package com.team.eventregistration.service;

import com.team.eventregistration.dto.MyWaitlistResponse;
import com.team.eventregistration.dto.SlotOfferResponse;
import com.team.eventregistration.dto.WaitlistEntryResponse;
import com.team.eventregistration.entity.*;
import com.team.eventregistration.exception.BusinessRuleException;
import com.team.eventregistration.exception.DuplicateWaitlistEntryException;
import com.team.eventregistration.exception.ResourceNotFoundException;
import com.team.eventregistration.repository.*;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class WaitlistService {
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final RegistrationRepository registrationRepository;
    private final SlotOfferRepository slotOfferRepository;

    public WaitlistService(
            WaitlistEntryRepository waitlistEntryRepository,
            EventRepository eventRepository,
            UserRepository userRepository,
            RegistrationRepository registrationRepository,
            SlotOfferRepository slotOfferRepository
    ) {
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.registrationRepository = registrationRepository;
        this.slotOfferRepository = slotOfferRepository;
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
    public MyWaitlistResponse getMyWaitlist(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        // 1. Query the user's active waitlist entries (WAITING or OFFERED)
        List<WaitlistEntry> activeEntries = waitlistEntryRepository
                .findByUser_IdAndStatusInOrderByJoinedAtAscIdAsc(
                        userId,
                        List.of(WaitlistEntryStatus.WAITING, WaitlistEntryStatus.OFFERED)
                );

        // 2. Map the active entries to WaitlistEntryResponse
        List<WaitlistEntryResponse> entryResponses = activeEntries.stream().map(entry -> {
            Integer position = null;

            // If the entry is in WAITING status, calculate its position in the waitlist
            if (entry.getStatus() == WaitlistEntryStatus.WAITING) {
                List<WaitlistEntry> eventWaitingList = waitlistEntryRepository
                        .findByEvent_IdAndStatusOrderByJoinedAtAscIdAsc(
                                entry.getEvent().getId(),
                                WaitlistEntryStatus.WAITING
                        );
                int index = eventWaitingList.indexOf(entry);
                position = (index >= 0) ? index + 1 : null;
            }

            // If the entry is in OFFERED status, find the corresponding PENDING offer
            SlotOfferResponse offerResponse = null;
            if (entry.getStatus() == WaitlistEntryStatus.OFFERED) {
                offerResponse = slotOfferRepository
                        .findByWaitlistEntry_IdAndStatus(entry.getId(), SlotOfferStatus.PENDING)
                        .map(offer -> new SlotOfferResponse(
                                offer.getId(),
                                offer.getStatus(),
                                offer.getExpiresAt() != null
                                        ? offer.getExpiresAt().atZone(ZoneOffset.UTC).toInstant()
                                        : null
                        ))
                .orElse(null);
            }
            return mapToMyWaitlistEntryResponse(entry, position, offerResponse);
        }).toList();

        // 3. Return MyWaitlistResponse containing the list of WaitlistEntryResponse
        return new MyWaitlistResponse(entryResponses, Instant.now());
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

    private WaitlistEntryResponse mapToMyWaitlistEntryResponse(
            WaitlistEntry entry, Integer position, SlotOfferResponse offer
    ) {
        WaitlistEntryResponse response = new WaitlistEntryResponse();
        response.setId(entry.getId());
        response.setEventId(entry.getEvent().getId());
        response.setEventTitle(entry.getEvent().getTitle());
        response.setUserId(entry.getUser().getId());
        response.setStatus(entry.getStatus());
        response.setJoinedAt(entry.getJoinedAt());
        response.setPosition(position);
        response.setOffer(offer);
        return response;
    }
}

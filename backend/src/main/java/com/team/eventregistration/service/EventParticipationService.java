package com.team.eventregistration.service;

import com.team.eventregistration.dto.OwnerWaitlistResponse;
import com.team.eventregistration.dto.ParticipantRemoval;
import com.team.eventregistration.dto.ParticipantResponse;
import com.team.eventregistration.entity.Event;
import com.team.eventregistration.entity.EventStatus;
import com.team.eventregistration.entity.Registration;
import com.team.eventregistration.entity.RegistrationStatus;
import com.team.eventregistration.exception.EventConflictException;
import com.team.eventregistration.exception.ResourceNotFoundException;
import com.team.eventregistration.repository.RegistrationRepository;
import com.team.eventregistration.service.port.EventOfferLifecycle;
import com.team.eventregistration.service.port.EventParticipation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class EventParticipationService implements EventParticipation {
    private final RegistrationRepository registrations;
    private final EventOfferLifecycle offers;

    public EventParticipationService(
            RegistrationRepository registrations,
            EventOfferLifecycle offers
    ) {
        this.registrations = registrations;
        this.offers = offers;
    }

    public void afterCapacityIncrease(Event event, LocalDateTime now) {
        allocate(event, now);
    }

    public void reopen(Event event, LocalDateTime now) {
        allocate(event, now);
    }

    public void close(Event event, LocalDateTime now) {
        offers.close(event, now);
    }

    public void delete(Event event, LocalDateTime now) {
        offers.delete(event, now);
    }

    public ParticipantRemoval removeParticipant(Event event, Long registrationId, Long actorId,
                                                String reason, LocalDateTime now) {
        Registration registration = registrations.findById(registrationId)
                .filter(item -> item.getEvent().getId().equals(event.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found in this event."));
        if (registration.getStatus() == RegistrationStatus.REMOVED)
            return new ParticipantRemoval(registration.getId(), registration.getUser().getId(), false);
        if (registration.getStatus() != RegistrationStatus.CONFIRMED)
            throw new EventConflictException("Only a confirmed participant can be removed.");
        if (!now.isBefore(event.getStartTime()))
            throw new EventConflictException("Participants cannot be removed after the event starts.");
        if (event.getRegisteredCount() <= 0)
            throw new EventConflictException("The event registration count is inconsistent.");
        registration.remove(now, actorId, reason);
        registrations.save(registration);
        event.setRegisteredCount(event.getRegisteredCount() - 1);
        allocate(event, now);
        return new ParticipantRemoval(registration.getId(), registration.getUser().getId(), true);
    }

    @Transactional(readOnly = true)
    public Optional<LocalDateTime> latestPendingExpiry(Event event) {
        return offers.latestPendingExpiry(event);
    }

    @Transactional(readOnly = true)
    public List<ParticipantResponse> participants(Event event) {
        return registrations.findByEvent_Id(event.getId()).stream()
                .sorted(Comparator.comparing(Registration::getRegisteredAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Registration::getId))
                .map(item -> new ParticipantResponse(item.getId(), item.getUser().getId(),
                        item.getUser().getDisplayName(), item.getUser().getEmail(), item.getStatus().name(),
                        item.getRegisteredAt(), item.getCancelledAt(), item.getRemovedAt(), item.getRemovedReason()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OwnerWaitlistResponse> waitingList(Event event) {
        return offers.waitingList(event);
    }

    @Transactional(readOnly = true)
    public long waitingCount(Event event) {
        return offers.waitingCount(event);
    }

    private void allocate(Event event, LocalDateTime now) {
        if (event.getDeletedAt() == null && event.getStatus() == EventStatus.OPEN
                && now.isBefore(event.getStartTime()))
            offers.allocateAvailable(event.getId(), now);
    }
}

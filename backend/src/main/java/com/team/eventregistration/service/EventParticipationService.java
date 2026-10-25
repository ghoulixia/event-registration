package com.team.eventregistration.service;

import com.team.eventregistration.dto.OwnerWaitlistResponse;
import com.team.eventregistration.dto.ParticipantRemoval;
import com.team.eventregistration.dto.ParticipantResponse;
import com.team.eventregistration.entity.Event;
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
    private final Optional<EventOfferLifecycle> offers;

    public EventParticipationService(RegistrationRepository registrations, Optional<EventOfferLifecycle> offers) {
        this.registrations = registrations;
        this.offers = offers;
    }

    public void afterCapacityIncrease(Event event, LocalDateTime now) { allocate(event, now); }
    public void reopen(Event event, LocalDateTime now) { allocate(event, now); }
    public void close(Event event, LocalDateTime now) {
        requireOfferHandler(event);
        offers.ifPresent(handler -> handler.close(event, now));
    }
    public void delete(Event event, LocalDateTime now) {
        requireOfferHandler(event);
        offers.ifPresent(handler -> handler.delete(event, now));
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
        requireOfferHandler(event);
        registration.remove(now, actorId, reason);
        event.setRegisteredCount(event.getRegisteredCount() - 1);
        registrations.save(registration);
        allocate(event, now);
        return new ParticipantRemoval(registration.getId(), registration.getUser().getId(), true);
    }

    @Transactional(readOnly = true)
    public Optional<LocalDateTime> latestPendingExpiry(Event event) {
        requireOfferHandler(event);
        return offers.flatMap(handler -> handler.latestPendingExpiry(event));
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
        return offers.map(handler -> handler.waitingList(event)).orElseGet(List::of);
    }
    @Transactional(readOnly = true)
    public long waitingCount(Event event) { return offers.map(handler -> handler.waitingCount(event)).orElse(0L); }

    private void allocate(Event event, LocalDateTime now) {
        requireOfferHandler(event);
        if (event.getDeletedAt() == null && event.getStatus() == com.team.eventregistration.entity.EventStatus.OPEN
                && now.isBefore(event.getStartTime()))
            offers.ifPresent(handler -> handler.allocateAvailable(event, now));
    }
    private void requireOfferHandler(Event event) {
        if (event.getHeldCount() > 0 && offers.isEmpty())
            throw new EventConflictException("Pending offers require their lifecycle handler before this action.");
    }
}

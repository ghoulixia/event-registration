package com.team.eventregistration.service;

import com.team.eventregistration.entity.*;
import com.team.eventregistration.exception.BusinessRuleException;
import com.team.eventregistration.exception.ResourceNotFoundException;
import com.team.eventregistration.exception.SlotOfferOutcomeException;
import com.team.eventregistration.repository.EventRepository;
import com.team.eventregistration.repository.RegistrationRepository;
import com.team.eventregistration.repository.SlotOfferRepository;
import com.team.eventregistration.repository.WaitlistEntryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class SlotOfferService {
    private final SlotOfferRepository slotOfferRepository;
    private final EventRepository eventRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final RegistrationRepository registrationRepository;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    public SlotOfferService(
            SlotOfferRepository slotOfferRepository,
            EventRepository eventRepository,
            WaitlistEntryRepository waitlistEntryRepository,
            RegistrationRepository registrationRepository
    ) {
        this(slotOfferRepository, eventRepository, waitlistEntryRepository,
                registrationRepository, Clock.systemDefaultZone());
    }

    public SlotOfferService(
            SlotOfferRepository slotOfferRepository,
            EventRepository eventRepository,
            WaitlistEntryRepository waitlistEntryRepository,
            RegistrationRepository registrationRepository,
            Clock clock
    ) {
        this.slotOfferRepository = slotOfferRepository;
        this.eventRepository = eventRepository;
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.registrationRepository = registrationRepository;
        this.clock = clock;
    }

    @Transactional
    public Optional<SlotOffer> offerNextWaitingEntry(Long eventId) {
        Event event = lockEvent(eventId);
        LocalDateTime now = now();

        if (!now.isBefore(event.getStartTime())) {
            finishWaitlistAtEventStartLocked(event, now);
            return Optional.empty();
        }
        if (!isOpenAndUpcoming(event, now)) {
            return Optional.empty();
        }

        return offerNextWaitingEntry(event, now);
    }

    @Transactional
    public Optional<SlotOffer> createNextOffer(Long eventId) {
        return offerNextWaitingEntry(eventId);
    }

    @Transactional
    public void allocateAvailableSlotsToWaitlist(Long eventId) {
        Event event = lockEvent(eventId);
        LocalDateTime now = now();
        if (!isOpenAndUpcoming(event, now)) {
            return;
        }

        while (availableSlots(event) > 0
                && offerNextWaitingEntry(event, now).isPresent()) {
            // Allocate every available slot before direct registration can use one.
        }
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = SlotOfferOutcomeException.class
    )
    public SlotOffer acceptOffer(Long userId, Long offerId) {
        SlotOffer offer = slotOfferRepository.findByIdAndUser_Id(offerId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Slot offer not found with ID: " + offerId));
        Event event = lockEvent(offer.getEvent().getId());
        refresh(offer);
        LocalDateTime now = now();

        if (offer.getStatus() != SlotOfferStatus.PENDING) {
            throw outcomeException(offer);
        }
        if (!now.isBefore(event.getStartTime())) {
            finishWaitlistAtEventStartLocked(event, now);
            throw outcomeException(offer);
        }
        validateOpenEvent(event, now, "accept a slot offer");

        WaitlistEntry entry = offer.getWaitlistEntry();
        validateOfferOwnership(offer, entry, event, userId);
        if (entry.getStatus() != WaitlistEntryStatus.OFFERED) {
            throw new BusinessRuleException(
                    "Slot offer is not associated with an offered waitlist entry.");
        }
        if (!now.isBefore(offer.getExpiresAt())) {
            expireOffer(offer, now);
            if (isOpenAndUpcoming(event, now)) {
                offerNextWaitingEntry(event, now);
            }
            throw outcomeException(offer);
        }

        Registration registration = registrationRepository
                .findByUser_IdAndEvent_Id(userId, event.getId())
                .orElse(null);
        if (registration != null && registration.getStatus() == RegistrationStatus.CONFIRMED) {
            throw new BusinessRuleException(
                    "User already has a confirmed registration for this event.");
        }

        entry.accept();
        offer.setStatus(SlotOfferStatus.ACCEPTED);
        offer.setResolvedAt(now);
        acceptHeldSlot(event);

        if (registration == null) {
            registration = new Registration(offer.getUser(), event);
        } else {
            registration.confirmAgain();
        }

        registrationRepository.save(registration);
        waitlistEntryRepository.save(entry);
        return slotOfferRepository.save(offer);
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = SlotOfferOutcomeException.class
    )
    public void declineOffer(Long userId, Long offerId) {
        SlotOffer offer = slotOfferRepository.findByIdAndUser_Id(offerId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Slot offer not found with ID: " + offerId));
        Event event = lockEvent(offer.getEvent().getId());
        refresh(offer);
        LocalDateTime now = now();

        if (offer.getStatus() != SlotOfferStatus.PENDING) {
            throw outcomeException(offer);
        }
        if (!now.isBefore(event.getStartTime())) {
            finishWaitlistAtEventStartLocked(event, now);
            throw outcomeException(offer);
        }
        validateOpenEvent(event, now, "decline a slot offer");

        WaitlistEntry entry = offer.getWaitlistEntry();
        validateOfferOwnership(offer, entry, event, userId);
        if (entry.getStatus() != WaitlistEntryStatus.OFFERED) {
            throw new BusinessRuleException(
                    "Slot offer is not associated with an offered waitlist entry.");
        }

        if (!now.isBefore(offer.getExpiresAt())) {
            expireOffer(offer, now);
            if (isOpenAndUpcoming(event, now)) {
                offerNextWaitingEntry(event, now);
            }
            throw outcomeException(offer);
        } else {
            entry.leave();
            offer.setStatus(SlotOfferStatus.DECLINED);
            offer.setResolvedAt(now);
            decrementHeldCount(event);
            waitlistEntryRepository.save(entry);
            slotOfferRepository.save(offer);
        }

        if (isOpenAndUpcoming(event, now)) {
            offerNextWaitingEntry(event, now);
        }
    }

    @Transactional
    public int expireOffers() {
        return expireOffers(now());
    }

    @Transactional
    public int expireOffers(LocalDateTime at) {
        List<SlotOffer> candidates = slotOfferRepository
                .findByStatusAndExpiresAtLessThanEqual(SlotOfferStatus.PENDING, at);
        Set<Long> processedEventIds = new HashSet<>();
        for (Event startedEvent : eventRepository.findByStartTimeLessThanEqual(at)) {
            processedEventIds.add(startedEvent.getId());
        }
        for (Event inactiveEvent : eventRepository.findInactiveWithActiveWaitlistEntries()) {
            processedEventIds.add(inactiveEvent.getId());
        }
        int expiredCount = 0;

        for (Long eventId : processedEventIds) {
            Event event = lockEvent(eventId);
            expiredCount += processEventOffersLocked(event, at);
        }

        for (SlotOffer candidate : candidates) {
            Long eventId = candidate.getEvent().getId();
            if (processedEventIds.contains(eventId)) {
                continue;
            }
            Event event = lockEvent(eventId);
            expiredCount += processEventOffersLocked(event, at);
            processedEventIds.add(eventId);
        }
        return expiredCount;
    }

    private int processEventOffersLocked(Event event, LocalDateTime at) {
        int newlyExpired = expirePendingOffers(event, at);
        if (!isOpenAndUpcoming(event, at)) {
            finishInactiveWaitlistLocked(event, at);
        } else if (newlyExpired > 0) {
            offerNextWaitingEntry(event, at);
        }
        return newlyExpired;
    }

    @Transactional
    public void finishWaitlistAtEventStart(Long eventId) {
        Event event = lockEvent(eventId);
        finishWaitlistAtEventStartLocked(event, now());
    }

    private void finishWaitlistAtEventStartLocked(Event event, LocalDateTime at) {
        if (at.isBefore(event.getStartTime())) {
            return;
        }

        finishInactiveWaitlistLocked(event, at);
    }

    private void finishInactiveWaitlistLocked(Event event, LocalDateTime at) {
        for (SlotOffer offer : slotOfferRepository.findByEvent_IdAndStatus(
                event.getId(), SlotOfferStatus.PENDING)) {
            if (at.isBefore(offer.getExpiresAt())) {
                revokeOffer(offer, at);
            } else {
                expireOffer(offer, at);
            }
        }

        for (WaitlistEntry entry : waitlistEntryRepository
                .findByEvent_IdAndStatusOrderByJoinedAtAscIdAsc(
                        event.getId(), WaitlistEntryStatus.WAITING)) {
            entry.cancel();
            waitlistEntryRepository.save(entry);
        }
        validateCounters(event);
    }

    private Optional<SlotOffer> offerNextWaitingEntry(Event event, LocalDateTime at) {
        validateOpenEvent(event, at, "offer a slot");
        expirePendingOffers(event, at);
        validateCounters(event);

        if (availableSlots(event) <= 0) {
            return Optional.empty();
        }

        List<WaitlistEntry> waitingEntries = waitlistEntryRepository
                .findByEvent_IdAndStatusOrderByJoinedAtAscIdAsc(
                        event.getId(), WaitlistEntryStatus.WAITING);
        for (WaitlistEntry entry : waitingEntries) {
            Long userId = entry.getUser().getId();
            Long eventId = event.getId();

            if (registrationRepository.existsByUser_IdAndEvent_IdAndStatus(
                    userId, eventId, RegistrationStatus.CONFIRMED)) {
                continue;
            }
            if (slotOfferRepository.findByUser_IdAndEvent_IdAndStatus(
                    userId, eventId, SlotOfferStatus.PENDING).isPresent()) {
                continue;
            }

            LocalDateTime expiresAt = at.plusMinutes(event.getOfferTtlMinutes());
            if (expiresAt.isAfter(event.getStartTime())) {
                expiresAt = event.getStartTime();
            }
            if (!expiresAt.isAfter(at)) {
                continue;
            }

            entry.offer();
            SlotOffer offer = new SlotOffer(
                    entry, event, entry.getUser(), expiresAt);
            event.setHeldCount(event.getHeldCount() + 1);
            validateCounters(event);
            waitlistEntryRepository.save(entry);
            return Optional.of(slotOfferRepository.save(offer));
        }
        return Optional.empty();
    }

    private int expirePendingOffers(Event event, LocalDateTime at) {
        int expiredCount = 0;
        for (SlotOffer offer : slotOfferRepository.findByEvent_IdAndStatus(
                event.getId(), SlotOfferStatus.PENDING)) {
            if (!at.isBefore(offer.getExpiresAt()) && expireOffer(offer, at)) {
                expiredCount++;
            }
        }
        return expiredCount;
    }

    private boolean expireOffer(SlotOffer offer, LocalDateTime at) {
        if (offer.getStatus() != SlotOfferStatus.PENDING) {
            return false;
        }
        WaitlistEntry entry = offer.getWaitlistEntry();
        if (entry.getStatus() != WaitlistEntryStatus.OFFERED) {
            throw new BusinessRuleException(
                    "Pending offer has an inconsistent waitlist entry status.");
        }

        offer.setStatus(SlotOfferStatus.EXPIRED);
        offer.setResolvedAt(at);
        entry.skip();
        releaseHeldSlotIfPresent(offer.getEvent());
        waitlistEntryRepository.save(entry);
        slotOfferRepository.save(offer);
        return true;
    }

    private void revokeOffer(SlotOffer offer, LocalDateTime at) {
        if (offer.getStatus() != SlotOfferStatus.PENDING) {
            return;
        }
        WaitlistEntry entry = offer.getWaitlistEntry();
        if (entry.getStatus() != WaitlistEntryStatus.OFFERED) {
            throw new BusinessRuleException(
                    "Pending offer has an inconsistent waitlist entry status.");
        }
        offer.setStatus(SlotOfferStatus.REVOKED);
        offer.setResolvedAt(at);
        entry.cancel();
        releaseHeldSlotIfPresent(offer.getEvent());
        waitlistEntryRepository.save(entry);
        slotOfferRepository.save(offer);
    }

    private SlotOfferOutcomeException outcomeException(SlotOffer offer) {
        return new SlotOfferOutcomeException(
                "Slot offer is " + offer.getStatus() + " and cannot be completed.");
    }

    private void validateOfferOwnership(
            SlotOffer offer,
            WaitlistEntry entry,
            Event event,
            Long userId
    ) {
        if (!entry.getEvent().getId().equals(event.getId())
                || !entry.getUser().getId().equals(userId)
                || !offer.getUser().getId().equals(userId)
                || !offer.getEvent().getId().equals(event.getId())) {
            throw new BusinessRuleException(
                    "Slot offer, waitlist entry and user do not match.");
        }
    }

    private Event lockEvent(Long eventId) {
        return eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Event not found with ID: " + eventId));
    }

    private void validateOpenEvent(Event event, LocalDateTime at, String operation) {
        if (event.getDeletedAt() != null || event.getStatus() != EventStatus.OPEN) {
            throw new BusinessRuleException(
                    "Cannot " + operation + " for a closed event.");
        }
        if (!at.isBefore(event.getStartTime())) {
            throw new BusinessRuleException(
                    "Cannot " + operation + " after the event has started.");
        }
    }

    private boolean isOpenAndUpcoming(Event event, LocalDateTime at) {
        return event.getDeletedAt() == null
                && event.getStatus() == EventStatus.OPEN
                && at.isBefore(event.getStartTime());
    }

    private int availableSlots(Event event) {
        return event.getCapacity() - event.getRegisteredCount() - event.getHeldCount();
    }

    private void decrementHeldCount(Event event) {
        if (event.getHeldCount() == null || event.getHeldCount() <= 0) {
            throw new BusinessRuleException("Event held count is inconsistent.");
        }
        event.setHeldCount(event.getHeldCount() - 1);
        validateCounters(event);
    }

    private void releaseHeldSlotIfPresent(Event event) {
        if (event.getHeldCount() == null) {
            throw new BusinessRuleException("Event held count is inconsistent.");
        }
        if (event.getHeldCount() > 0) {
            decrementHeldCount(event);
        }
    }

    private void acceptHeldSlot(Event event) {
        if (event.getHeldCount() == null || event.getHeldCount() <= 0) {
            throw new BusinessRuleException("Event has no held slot for this offer.");
        }
        if (event.getRegisteredCount() >= event.getCapacity()
                || availableSlots(event) < 0) {
            throw new BusinessRuleException(
                    "Event capacity counters are inconsistent.");
        }
        event.setHeldCount(event.getHeldCount() - 1);
        event.setRegisteredCount(event.getRegisteredCount() + 1);
        validateCounters(event);
    }

    private void validateCounters(Event event) {
        if (event.getRegisteredCount() == null
                || event.getHeldCount() == null
                || event.getCapacity() == null
                || event.getRegisteredCount() < 0
                || event.getHeldCount() < 0
                || event.getRegisteredCount() + event.getHeldCount() > event.getCapacity()) {
            throw new BusinessRuleException(
                    "Event capacity counters are inconsistent.");
        }
    }

    private void refresh(SlotOffer offer) {
        if (entityManager != null) {
            entityManager.refresh(offer);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}

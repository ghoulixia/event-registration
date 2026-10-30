package com.team.eventregistration.service;

import com.team.eventregistration.dto.OwnerWaitlistResponse;
import com.team.eventregistration.entity.*;
import com.team.eventregistration.exception.BusinessRuleException;
import com.team.eventregistration.exception.ResourceNotFoundException;
import com.team.eventregistration.exception.SlotOfferOutcomeException;
import com.team.eventregistration.repository.EventRepository;
import com.team.eventregistration.repository.RegistrationRepository;
import com.team.eventregistration.repository.SlotOfferRepository;
import com.team.eventregistration.repository.WaitlistEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SlotOfferService {
    private final SlotOfferRepository slotOfferRepository;
    private final EventRepository eventRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final RegistrationRepository registrationRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Autowired
    public SlotOfferService(
            SlotOfferRepository slotOfferRepository,
            EventRepository eventRepository,
            WaitlistEntryRepository waitlistEntryRepository,
            RegistrationRepository registrationRepository,
            AuditLogService auditLogService,
            Clock clock
    ) {
        this.slotOfferRepository = slotOfferRepository;
        this.eventRepository = eventRepository;
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.registrationRepository = registrationRepository;
        this.auditLogService = auditLogService;
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

        int releasedCount = revokeRemovedParticipantOffers(event, now) + expirePendingOffers(event, now);
        Optional<SlotOffer> nextOffer = offerNextWaitingEntry(event, now);
        if (releasedCount > 0) {
            allocateAvailableSlotsToWaitlist(event, now);
        }
        return nextOffer;
    }

    @Transactional
    public Optional<SlotOffer> createNextOffer(Long eventId) {
        return offerNextWaitingEntry(eventId);
    }

    @Transactional
    public void allocateAvailableSlotsToWaitlist(Long eventId) {
        Event event = lockEvent(eventId);
        allocateAvailableSlotsToWaitlist(event, now());
    }

    @Transactional
    public void allocateAvailableSlotsToWaitlist(Long eventId, LocalDateTime at) {
        Event event = lockEvent(eventId);
        allocateAvailableSlotsToWaitlist(event, now());
    }

    @Transactional
    public void allocateAvailableSlotsToWaitlist(Event event, LocalDateTime at) {
        if (!isOpenAndUpcoming(event, at)) {
            return;
        }

        revokeRemovedParticipantOffers(event, at);
        expirePendingOffers(event, at);
        validateCounters(event);
        while (availableSlots(event) > 0) {
            Optional<SlotOffer> offer = offerNextWaitingEntry(event, at);
            if (offer.isEmpty()) {
                break;
            }
        }
    }

    @Transactional
    public void close(Event event, LocalDateTime at) {
        if (at.isBefore(event.getStartTime())) {
            finishTemporarilyClosedWaitlistLocked(event, at);
        } else {
            finishInactiveWaitlistLocked(event, at);
        }
    }

    @Transactional
    public void delete(Event event, LocalDateTime at) {
        finishDeletedWaitlistLocked(event, at);
    }

    @Transactional(readOnly = true)
    public Optional<LocalDateTime> latestPendingExpiry(Event event) {
        return slotOfferRepository.findByEvent_IdAndStatus(event.getId(), SlotOfferStatus.PENDING)
                .stream()
                .map(SlotOffer::getExpiresAt)
                .max(Comparator.naturalOrder());
    }

    @Transactional(readOnly = true)
    public long waitingCount(Event event) {
        return waitlistEntryRepository
                .findByEvent_IdAndStatusOrderByJoinedAtAscIdAsc(
                        event.getId(), WaitlistEntryStatus.WAITING)
                .size();
    }

    @Transactional(readOnly = true)
    public List<OwnerWaitlistResponse> waitingList(Event event) {
        List<WaitlistEntry> waiting = waitlistEntryRepository
                .findByEvent_IdAndStatusOrderByJoinedAtAscIdAsc(
                        event.getId(), WaitlistEntryStatus.WAITING);
        List<WaitlistEntry> offered = waitlistEntryRepository
                .findByEvent_IdAndStatusOrderByJoinedAtAscIdAsc(
                        event.getId(), WaitlistEntryStatus.OFFERED);

        List<WaitlistEntry> entries = new ArrayList<>(waiting);
        entries.addAll(offered);
        return entries.stream()
                .map(entry -> new OwnerWaitlistResponse(
                        entry.getId(),
                        event.getId(),
                        event.getTitle(),
                        entry.getUser().getId(),
                        entry.getUser().getDisplayName(),
                        entry.getStatus().name(),
                        entry.getJoinedAt(),
                        entry.getStatus() == WaitlistEntryStatus.WAITING
                                ? waiting.indexOf(entry) + 1
                                : null))
                .toList();
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
        Registration registration = registrationRepository
                .findByUser_IdAndEvent_Id(userId, event.getId())
                .orElse(null);
        if (registration != null && registration.getStatus() == RegistrationStatus.REMOVED) {
            revokeOffer(offer, now);
            allocateAvailableSlotsToWaitlist(event, now);
            throw outcomeException(offer);
        }
        if (!now.isBefore(offer.getExpiresAt())) {
            expireOffer(offer, now);
            allocateAvailableSlotsToWaitlist(event, now);
            throw outcomeException(offer);
        }
        if (registration != null && registration.getStatus() == RegistrationStatus.CONFIRMED) {
            throw new BusinessRuleException(
                    "User already has a confirmed registration for this event.");
        }

        String previousStatus = (registration != null) ? registration.getStatus().name() : "NONE";

        entry.accept();
        offer.setStatus(SlotOfferStatus.ACCEPTED);
        offer.setResolvedAt(now);
        acceptHeldSlot(event);

        if (registration == null) {
            registration = new Registration(offer.getUser(), event, now);
        } else {
            registration.confirmAgain(now);
        }

        Registration savedRegistration = registrationRepository.saveAndFlush(registration);
        waitlistEntryRepository.save(entry);
        SlotOffer savedOffer = slotOfferRepository.save(offer);

        Map<String, Object> details = Map.of(
                "previous_status", previousStatus,
                "new_status", RegistrationStatus.CONFIRMED.name(),
                "registration_id", savedRegistration.getId(),
                "user_id", userId,
                "offer_id", savedOffer.getId(),
                "registered_at", now.toString()
        );

        auditLogService.recordEventAction(
                event.getId(),
                userId,
                "REGISTRATION_CONFIRM",
                "Accepted slot offer from waitlist",
                details
        );

        return savedOffer;
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
            allocateAvailableSlotsToWaitlist(event, now);
            throw outcomeException(offer);
        } else {
            entry.leave(now);
            offer.setStatus(SlotOfferStatus.DECLINED);
            offer.setResolvedAt(now);
            decrementHeldCount(event);
            waitlistEntryRepository.save(entry);
            slotOfferRepository.save(offer);
        }

        allocateAvailableSlotsToWaitlist(event, now);
    }

    @Transactional
    public void withdrawOffer(Event event, SlotOffer offer, LocalDateTime at) {
        if (offer.getStatus() != SlotOfferStatus.PENDING
                || offer.getEvent().getId() == null
                || !offer.getEvent().getId().equals(event.getId())) {
            throw new BusinessRuleException("Slot offer is not pending for this event.");
        }

        WaitlistEntry entry = offer.getWaitlistEntry();
        if (entry.getStatus() != WaitlistEntryStatus.OFFERED) {
            throw new BusinessRuleException(
                    "Pending offer is not associated with an offered waitlist entry.");
        }

        if (!at.isBefore(offer.getExpiresAt())) {
            expireOffer(offer, at);
        } else {
            entry.leave(at);
            offer.setStatus(SlotOfferStatus.DECLINED);
            offer.setResolvedAt(at);
            decrementHeldCount(event);
            waitlistEntryRepository.save(entry);
            slotOfferRepository.save(offer);
        }

        allocateAvailableSlotsToWaitlist(event, at);
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
            expiredCount += processEventOffersLocked(event, now());
        }

        for (SlotOffer candidate : candidates) {
            Long eventId = candidate.getEvent().getId();
            if (processedEventIds.contains(eventId)) {
                continue;
            }
            Event event = lockEvent(eventId);
            expiredCount += processEventOffersLocked(event, now());
            processedEventIds.add(eventId);
        }
        return expiredCount;
    }

    @Transactional
    public void finishWaitlistAtEventStart(Long eventId) {
        Event event = lockEvent(eventId);
        finishWaitlistAtEventStartLocked(event, now());
    }

    private int processEventOffersLocked(Event event, LocalDateTime at) {
        if (event.getDeletedAt() != null) {
            finishDeletedWaitlistLocked(event, at);
            return 0;
        }
        if (!at.isBefore(event.getStartTime())) {
            int newlyExpired = expirePendingOffers(event, at);
            finishInactiveWaitlistLocked(event, at);
            return newlyExpired;
        }
        if (event.getStatus() != EventStatus.OPEN) {
            finishTemporarilyClosedWaitlistLocked(event, at);
            return 0;
        }

        int newlyExpired = expirePendingOffers(event, at);
        if (newlyExpired > 0) {
            allocateAvailableSlotsToWaitlist(event, at);
        }
        return newlyExpired;
    }

    private void finishWaitlistAtEventStartLocked(Event event, LocalDateTime at) {
        if (at.isBefore(event.getStartTime())) {
            return;
        }

        finishInactiveWaitlistLocked(event, at);
    }

    private void finishTemporarilyClosedWaitlistLocked(Event event, LocalDateTime at) {
        for (SlotOffer offer : slotOfferRepository.findByEvent_IdAndStatus(
                event.getId(), SlotOfferStatus.PENDING)) {
            if (at.isBefore(offer.getExpiresAt())) {
                revokeOfferAndRestoreWaiting(offer, at);
            } else {
                expireOffer(offer, at);
            }
        }
        validateCounters(event);
    }

    private void finishDeletedWaitlistLocked(Event event, LocalDateTime at) {
        for (SlotOffer offer : slotOfferRepository.findByEvent_IdAndStatus(
                event.getId(), SlotOfferStatus.PENDING)) {
            revokeOffer(offer, at);
        }

        for (WaitlistEntry entry : waitlistEntryRepository
                .findByEvent_IdAndStatusOrderByJoinedAtAscIdAsc(
                        event.getId(), WaitlistEntryStatus.WAITING)) {
            entry.cancel();
            waitlistEntryRepository.save(entry);
        }
        validateCounters(event);
    }

    private void finishInactiveWaitlistLocked(Event event, LocalDateTime at) {
        for (SlotOffer offer : slotOfferRepository.findByEvent_IdAndStatus(
                event.getId(), SlotOfferStatus.PENDING)) {
            expireOffer(offer, at);
        }

        for (WaitlistEntry entry : waitlistEntryRepository
                .findByEvent_IdAndStatusOrderByJoinedAtAscIdAsc(
                        event.getId(), WaitlistEntryStatus.WAITING)) {
            entry.cancel();
            waitlistEntryRepository.save(entry);
        }
        validateCounters(event);
    }

    private void revokeOfferAndRestoreWaiting(SlotOffer offer, LocalDateTime at) {
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
        entry.restoreWaiting();
        releaseHeldSlotIfPresent(offer.getEvent());
        waitlistEntryRepository.save(entry);
        slotOfferRepository.save(offer);
    }

    private Optional<SlotOffer> offerNextWaitingEntry(Event event, LocalDateTime at) {
        validateOpenEvent(event, at, "offer a slot");
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
                    userId, eventId, RegistrationStatus.REMOVED)) {
                entry.cancel();
                waitlistEntryRepository.save(entry);
                continue;
            }
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
                    entry, event, entry.getUser(), at, expiresAt);
            event.setHeldCount(event.getHeldCount() + 1);
            validateCounters(event);
            waitlistEntryRepository.save(entry);
            return Optional.of(slotOfferRepository.save(offer));
        }
        return Optional.empty();
    }

    private int revokeRemovedParticipantOffers(Event event, LocalDateTime at) {
        int revokedCount = 0;
        for (SlotOffer offer : slotOfferRepository.findByEvent_IdAndStatus(
                event.getId(), SlotOfferStatus.PENDING)) {
            if (registrationRepository.existsByUser_IdAndEvent_IdAndStatus(
                    offer.getUser().getId(), event.getId(), RegistrationStatus.REMOVED)) {
                revokeOffer(offer, at);
                revokedCount++;
            }
        }
        return revokedCount;
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
        slotOfferRepository.refresh(offer);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}

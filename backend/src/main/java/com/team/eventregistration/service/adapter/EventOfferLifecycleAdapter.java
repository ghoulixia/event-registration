package com.team.eventregistration.service.adapter;

import com.team.eventregistration.dto.OwnerWaitlistResponse;
import com.team.eventregistration.entity.Event;
import com.team.eventregistration.entity.SlotOffer;
import com.team.eventregistration.service.SlotOfferService;
import com.team.eventregistration.service.port.EventOfferLifecycle;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class EventOfferLifecycleAdapter implements EventOfferLifecycle {
    private final SlotOfferService slotOffers;

    public EventOfferLifecycleAdapter(SlotOfferService slotOffers) {
        this.slotOffers = slotOffers;
    }

    @Override
    public void allocateAvailable(Long eventId, LocalDateTime now) {
        slotOffers.allocateAvailableSlotsToWaitlist(eventId, now);
    }

    @Override
    public void allocateAvailableForRegistration(Long eventId) {
        slotOffers.allocateAvailableSlotsToWaitlist(eventId);
    }

    @Override
    public void withdrawOffer(Event event, SlotOffer offer, LocalDateTime now) {
        slotOffers.withdrawOffer(event, offer, now);
    }

    @Override
    public void close(Event event, LocalDateTime now) {
        slotOffers.close(event, now);
    }

    @Override
    public void delete(Event event, LocalDateTime now) {
        slotOffers.delete(event, now);
    }

    @Override
    public Optional<LocalDateTime> latestPendingExpiry(Event event) {
        return slotOffers.latestPendingExpiry(event);
    }

    @Override
    public long waitingCount(Event event) {
        return slotOffers.waitingCount(event);
    }

    @Override
    public List<OwnerWaitlistResponse> waitingList(Event event) {
        return slotOffers.waitingList(event);
    }
}

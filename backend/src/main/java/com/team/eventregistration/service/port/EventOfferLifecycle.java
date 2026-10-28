package com.team.eventregistration.service.port;

import com.team.eventregistration.dto.OwnerWaitlistResponse;
import com.team.eventregistration.entity.Event;
import com.team.eventregistration.entity.SlotOffer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventOfferLifecycle {
    void allocateAvailable(Event event, LocalDateTime now);
    default void withdrawOffer(Event event, SlotOffer offer, LocalDateTime now) {
        throw new UnsupportedOperationException("Offer withdrawal is not supported.");
    }
    void close(Event event, LocalDateTime now);
    void delete(Event event, LocalDateTime now);
    Optional<LocalDateTime> latestPendingExpiry(Event event);
    long waitingCount(Event event);
    List<OwnerWaitlistResponse> waitingList(Event event);
}

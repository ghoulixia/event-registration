package com.team.eventregistration.service.adapter;

import com.team.eventregistration.dto.OwnerWaitlistResponse;
import com.team.eventregistration.entity.Event;
import com.team.eventregistration.entity.SlotOffer;
import com.team.eventregistration.service.SlotOfferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventOfferLifecycleAdapterTest {
    @Mock private SlotOfferService slotOffers;
    @Mock private Event event;
    @Mock private SlotOffer offer;
    private Clock clock;

    @BeforeEach
    void setUp() {
        Instant fixedInstant = Instant.parse("2026-10-10T10:00:00Z");
        ZoneId zoneId = ZoneId.of("Asia/Ho_Chi_Minh");
        clock = Clock.fixed(fixedInstant, zoneId);
    }

    @Test
    void delegatesEveryLifecycleOperationToSlotOfferService() {
        EventOfferLifecycleAdapter adapter = new EventOfferLifecycleAdapter(slotOffers);
        LocalDateTime now = LocalDateTime.now(clock);
        List<OwnerWaitlistResponse> waiting = List.of();

        when(event.getId()).thenReturn(1L);
        when(slotOffers.latestPendingExpiry(event)).thenReturn(Optional.of(now));
        when(slotOffers.waitingCount(event)).thenReturn(2L);
        when(slotOffers.waitingList(event)).thenReturn(waiting);

        adapter.allocateAvailable(event.getId(), now);
        adapter.allocateAvailableForRegistration(8L);
        adapter.withdrawOffer(event, offer, now);
        adapter.close(event, now);
        adapter.delete(event, now);

        assertEquals(Optional.of(now), adapter.latestPendingExpiry(event));
        assertEquals(2L, adapter.waitingCount(event));
        assertEquals(waiting, adapter.waitingList(event));

        verify(slotOffers).allocateAvailableSlotsToWaitlist(1L, now);
        verify(slotOffers).allocateAvailableSlotsToWaitlist(8L);
        verify(slotOffers).withdrawOffer(event, offer, now);
        verify(slotOffers).close(event, now);
        verify(slotOffers).delete(event, now);
    }
}

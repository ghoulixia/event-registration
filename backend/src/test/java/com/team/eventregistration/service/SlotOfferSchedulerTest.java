package com.team.eventregistration.service;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SlotOfferSchedulerTest {

    @Test
    void scheduledProcessingDelegatesToSlotOfferService() throws NoSuchMethodException {
        SlotOfferService slotOfferService = mock(SlotOfferService.class);
        SlotOfferScheduler scheduler = new SlotOfferScheduler(slotOfferService);

        scheduler.processPendingOffers();

        verify(slotOfferService).expireOffers();
        Scheduled scheduled = SlotOfferScheduler.class
                .getMethod("processPendingOffers")
                .getAnnotation(Scheduled.class);
        org.junit.jupiter.api.Assertions.assertNotNull(scheduled);
    }
}

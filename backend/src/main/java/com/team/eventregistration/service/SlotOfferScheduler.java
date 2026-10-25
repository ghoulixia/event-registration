package com.team.eventregistration.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the same locked service workflow used by user-driven offer outcomes.
 */
@Component
public class SlotOfferScheduler {
    private final SlotOfferService slotOfferService;

    public SlotOfferScheduler(SlotOfferService slotOfferService) {
        this.slotOfferService = slotOfferService;
    }

    @Scheduled(fixedDelayString = "${app.waitlist.processing-interval-ms:60000}")
    public void processPendingOffers() {
        slotOfferService.expireOffers();
    }
}

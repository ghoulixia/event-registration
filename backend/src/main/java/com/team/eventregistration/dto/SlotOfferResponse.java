package com.team.eventregistration.dto;

import com.team.eventregistration.entity.SlotOfferStatus;

import java.time.Instant;

public class SlotOfferResponse {
    private Long id;
    private SlotOfferStatus status;
    private Instant expiresAt;

    public SlotOfferResponse(
            Long id,
            SlotOfferStatus status,
            Instant expiresAt
    ) {
        this.id = id;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public SlotOfferStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}

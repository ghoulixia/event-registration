package com.team.eventregistration.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "slot_offers")
public class SlotOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "waitlist_entry_id", nullable = false)
    private WaitlistEntry waitlistEntry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SlotOfferStatus status = SlotOfferStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    protected SlotOffer() {
    }

    public SlotOffer(
            WaitlistEntry waitlistEntry,
            Event event,
            User user,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {
        this.waitlistEntry = waitlistEntry;
        this.event = event;
        this.user = user;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = SlotOfferStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public WaitlistEntry getWaitlistEntry() {
        return waitlistEntry;
    }

    public Event getEvent() {
        return event;
    }

    public User getUser() {
        return user;
    }

    public SlotOfferStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setStatus(SlotOfferStatus status) {
        this.status = status;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
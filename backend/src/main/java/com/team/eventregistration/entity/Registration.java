package com.team.eventregistration.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "registrations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_registrations_user_event",
                        columnNames = {"user_id", "event_id"}
                )
        }
)
public class Registration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationStatus status;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "removed_at")
    private LocalDateTime removedAt;
    @Column(name = "removed_reason", length = 1000)
    private String removedReason;
    @Column(name = "removed_by_user_id")
    private Long removedByUserId;

    protected Registration() {
    }

    public Registration(User user, Event event) {
        this(user, event, LocalDateTime.now());
    }

    public Registration(User user, Event event, LocalDateTime now) {
        this.registeredAt = now;
        this.user = user;
        this.event = event;
        this.status = RegistrationStatus.CONFIRMED;
    }

    public void cancel() {
        if (this.status == RegistrationStatus.CANCELLED) {
            return;
        }

        this.status = RegistrationStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    public void confirmAgain() {
        confirmAgain(LocalDateTime.now());
    }

    public void confirmAgain(LocalDateTime now) {
        if (this.status != RegistrationStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Only cancelled registrations can be confirmed again."
            );
        }

        this.status = RegistrationStatus.CONFIRMED;
        this.registeredAt = now;
        this.cancelledAt = null;
    }

    public void remove(LocalDateTime now, Long actorId, String reason) {
        if (status != RegistrationStatus.CONFIRMED) throw new IllegalStateException("Registration is not confirmed.");
        status = RegistrationStatus.REMOVED;
        removedAt = now;
        removedByUserId = actorId;
        removedReason = reason;
    }

    public LocalDateTime getRemovedAt() { return removedAt; }
    public String getRemovedReason() { return removedReason; }
    public Long getRemovedByUserId() { return removedByUserId; }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Event getEvent() {
        return event;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }
}
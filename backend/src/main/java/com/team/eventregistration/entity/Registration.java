package com.team.eventregistration.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "registrations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_registration_user_event",
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

    @CreationTimestamp
    @Column(name = "registered_at", nullable = false, updatable = false)
    private LocalDateTime registeredAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    protected Registration() {
    }

    public Registration(User user, Event event) {
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
        if (this.status != RegistrationStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Only cancelled registrations can be confirmed again."
            );
        }

        this.status = RegistrationStatus.CONFIRMED;
        this.cancelledAt = null;
    }

    // --- Getters ---

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
package com.team.eventregistration.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "waitlist_entries")
public class WaitlistEntry {
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
    private WaitlistEntryStatus status;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    protected WaitlistEntry() {
    }

    public WaitlistEntry(User user, Event event) {
        this.user = user;
        this.event = event;
        this.status = WaitlistEntryStatus.WAITING;
    }

    public void offer() {
        if (this.status != WaitlistEntryStatus.WAITING) {
            throw new IllegalStateException(
                    "Only WAITING entries can be offered a slot."
            );
        }
        this.status = WaitlistEntryStatus.OFFERED;
    }

    public void accept() {
        if (this.status != WaitlistEntryStatus.OFFERED) {
            throw new IllegalStateException(
                    "Only OFFERED entries can be accepted."
            );
        }
        this.status = WaitlistEntryStatus.ACCEPTED;
    }

    public void leave() {
        leave(LocalDateTime.now());
    }

    public void leave(LocalDateTime leftAt) {
        if (this.status == WaitlistEntryStatus.LEFT) {
            return;
        }
        this.status = WaitlistEntryStatus.LEFT;
        this.leftAt = leftAt;
    }

    public void skip() {
        if (this.status != WaitlistEntryStatus.OFFERED) {
            throw new IllegalStateException(
                    "Only OFFERED entries can be skipped."
            );
        }
        this.status = WaitlistEntryStatus.SKIPPED;
    }

    public void cancel() {
        if (this.status != WaitlistEntryStatus.WAITING
                && this.status != WaitlistEntryStatus.OFFERED) {
            throw new IllegalStateException(
                    "Only active waitlist entries can be canceled."
            );
        }
        this.status = WaitlistEntryStatus.CANCELLED;
        this.leftAt = LocalDateTime.now();
    }

    public void restoreWaiting() {
        if (this.status != WaitlistEntryStatus.OFFERED) {
            throw new IllegalStateException(
                    "Only OFFERED entries can be restored to WAITING."
            );
        }
        this.status = WaitlistEntryStatus.WAITING;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Event getEvent() { return event; }
    public WaitlistEntryStatus getStatus() { return status; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public LocalDateTime getLeftAt() { return leftAt; }

    public void setId(Long id) { this.id = id; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
}

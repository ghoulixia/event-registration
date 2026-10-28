package com.team.eventregistration.dto;

import com.team.eventregistration.entity.WaitlistEntryStatus;

import java.time.LocalDateTime;

public class WaitlistEntryResponse {
    private Long id;
    private Long eventId;
    private String eventTitle;
    private Long userId;
    private WaitlistEntryStatus status;
    private LocalDateTime joinedAt;
    private Integer position;
    private SlotOfferResponse offer;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
    public String getEventTitle() { return eventTitle; }
    public void setEventTitle(String eventTitle) { this.eventTitle = eventTitle; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public WaitlistEntryStatus getStatus() { return status; }
    public void setStatus(WaitlistEntryStatus status) { this.status = status; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
    public SlotOfferResponse getOffer() { return offer; }
    public void setOffer(SlotOfferResponse offer) { this.offer = offer; }
}

package com.team.eventregistration.dto;

import com.team.eventregistration.entity.RegistrationStatus;

import java.time.LocalDateTime;
import java.util.List;

public class RegistrationResponse {
    private Long id;
    private RegistrationStatus status;
    private Long eventId;
    private String eventTitle;
    private LocalDateTime registeredAt;
    private List<RegistrationHistoryResponse> history = List.of();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public void setStatus(RegistrationStatus status) {
        this.status = status;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public void setEventTitle(String eventTitle) {
        this.eventTitle = eventTitle;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public List<RegistrationHistoryResponse> getHistory() {
        return history;
    }

    public void setHistory(List<RegistrationHistoryResponse> history) {
        this.history = history;
    }
}

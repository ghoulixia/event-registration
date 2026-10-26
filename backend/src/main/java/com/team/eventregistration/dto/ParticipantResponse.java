package com.team.eventregistration.dto;
import java.time.LocalDateTime;
public record ParticipantResponse(Long id, Long userId, String displayName, String email, String status,
        LocalDateTime registeredAt, LocalDateTime cancelledAt, LocalDateTime removedAt,
        String removedReason) {}

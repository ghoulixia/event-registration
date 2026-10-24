package com.team.eventregistration.dto;

import java.time.Instant;

public record AuditResponse(Long id, String action, String reason, Instant createdAt) {
}

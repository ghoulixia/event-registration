package com.team.eventregistration.dto;

import java.time.Instant;
import java.util.Map;

public record AuditResponse(Long id, String action, String reason, Instant createdAt,
                            Long actorUserId, String actorName, Map<String, Object> details) {}

package com.team.eventregistration.dto;

import java.time.Instant;

public record RegistrationHistoryResponse(String action, Instant createdAt,
                                          String previousStatus, String newStatus) {
}

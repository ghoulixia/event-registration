package com.team.eventregistration.dto;

import java.time.LocalDateTime;

public record OwnerWaitlistResponse(Long id, Long eventId, String eventTitle, Long userId,
                                    String displayName, String status, LocalDateTime joinedAt, Integer position) {}

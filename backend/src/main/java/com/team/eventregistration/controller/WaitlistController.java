package com.team.eventregistration.controller;

import com.team.eventregistration.dto.MyWaitlistResponse;
import com.team.eventregistration.dto.WaitlistEntryResponse;
import com.team.eventregistration.service.WaitlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Waitlist", description = "Manage event waitlists")
@SecurityRequirement(name = "bearerAuth")
public class WaitlistController {
    private final WaitlistService waitlistService;

    public WaitlistController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @PostMapping("/events/{id}/waitlist")
    @Operation(summary = "Join an event waitlist")
    public ResponseEntity<WaitlistEntryResponse> join(
            @PathVariable Long id, @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(waitlistService.joinWaitlist(userId, id));
    }

    @DeleteMapping("/events/{id}/waitlist/me")
    @Operation(summary = "Leave an event waitlist")
    public ResponseEntity<Void> leave(
            @PathVariable Long id, @AuthenticationPrincipal Long userId) {
        waitlistService.leaveWaitlist(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/waitlist")
    @Operation(summary = "View my active waitlist entries")
    public ResponseEntity<MyWaitlistResponse> getMyWaitlist(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(waitlistService.getMyWaitlist(userId));
    }
}

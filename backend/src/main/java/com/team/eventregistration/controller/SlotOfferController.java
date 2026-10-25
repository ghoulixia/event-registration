package com.team.eventregistration.controller;

import com.team.eventregistration.service.SlotOfferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/offers")
@Tag(name = "Slot offers", description = "Accept or decline waitlist slot offers")
@SecurityRequirement(name = "bearerAuth")
public class SlotOfferController {
    private final SlotOfferService slotOfferService;

    public SlotOfferController(SlotOfferService slotOfferService) {
        this.slotOfferService = slotOfferService;
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Accept a slot offer")
    public ResponseEntity<Void> accept(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId
    ) {
        slotOfferService.acceptOffer(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/decline")
    @Operation(summary = "Decline a slot offer")
    public ResponseEntity<Void> decline(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId
    ) {
        slotOfferService.declineOffer(userId, id);
        return ResponseEntity.noContent().build();
    }
}

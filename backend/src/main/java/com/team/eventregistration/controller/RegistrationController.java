package com.team.eventregistration.controller;

import com.team.eventregistration.dto.RegistrationResponse;
import com.team.eventregistration.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Registrations", description = "Register for events")
@SecurityRequirement(name = "bearerAuth")
public class RegistrationController {
    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/events/{id}/registrations")
    @Operation(summary = "Register for an event", description = "Register for an event by ID.")
    @ApiResponse(responseCode = "200", description = "Registration successful")
    @ApiResponse(responseCode = "400", description = "Invalid registration request")
    @ApiResponse(responseCode = "404", description = "User or Event not found")
    @ApiResponse(responseCode = "409", description = "Event is full or User has already registered for this event")
    public ResponseEntity<RegistrationResponse> registerForEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId
    ) {
        RegistrationResponse response = registrationService.registerForEvent(userId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/registrations")
    @Operation(summary = "Get my registrations", description = "Get all registrations for the authenticated user.")
    @ApiResponse(responseCode = "200", description = "Registrations retrieved successfully")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<List<RegistrationResponse>> getMyRegistrations(
            @AuthenticationPrincipal Long userId
    ) {
        List<RegistrationResponse> responses = registrationService.getMyRegistrations(userId);
        return ResponseEntity.ok(responses);
    }

    @DeleteMapping("/registrations/{id}")
    @Operation(summary = "Cancel a registration", description = "Cancel an authenticated user's registration by ID.")
    @ApiResponse(responseCode = "204", description = "Registration cancelled")
    @ApiResponse(responseCode = "400", description = "Event has already started or cancellation is not allowed")
    @ApiResponse(responseCode = "404", description = "Registration not found")
    public ResponseEntity<Void> cancelRegistration(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId
    ) {
        registrationService.cancelRegistration(userId, id);
        return ResponseEntity.noContent().build();
    }
}

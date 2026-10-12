package com.team.eventregistration.controller;

import com.team.eventregistration.dto.RegistrationResponse;
import com.team.eventregistration.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Registrations (Public)", description = "Register for events")
public class RegistrationController {
    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /**
     * NOTE: Long usedId is temporary for testing purposes.
     * In a real application, the user ID should be obtained from the authenticated user's session or token.
     *
     * @param id the ID of the event to register for
     * @param userId the ID of the user registering
     * @return the registration response
     */
    @PostMapping("/events/{id}/registrations")
    @Operation(summary = "Register for an event", description = "Register for an event by ID.")
    @ApiResponse(responseCode = "200", description = "Registration successful")
    @ApiResponse(responseCode = "400", description = "Invalid registration request")
    @ApiResponse(responseCode = "404", description = "User or Event not found")
    @ApiResponse(responseCode = "409", description = "User has already registered for this event")
    public ResponseEntity<RegistrationResponse> registerForEvent(@PathVariable Long id, @RequestParam Long userId) {
        RegistrationResponse response = registrationService.registerForEvent(userId, id);
        return ResponseEntity.ok(response);
    }
}

package com.team.eventregistration.controller;

import com.team.eventregistration.dto.*;
import com.team.eventregistration.service.EventService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Event ownership")
@SecurityRequirement(name = "bearerAuth")
public class OwnerEventController {
    private final EventService events;
    public OwnerEventController(EventService events) { this.events = events; }
    @PostMapping
    public ResponseEntity<EventResponse> create(Principal actor, @Valid @RequestBody CreateEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(events.createEvent(actorId(actor), request));
    }
    @PutMapping("/{id}")
    public EventResponse update(Principal actor, @PathVariable Long id, @Valid @RequestBody UpdateEventRequest request) {
        return events.updateEvent(actorId(actor), id, request);
    }
    @PatchMapping("/{id}/status")
    public EventResponse status(Principal actor, @PathVariable Long id, @Valid @RequestBody EventStatusRequest request) {
        return events.updateStatus(actorId(actor), id, request.status());
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Principal actor, @PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        events.softDeleteEvent(actorId(actor), id, request.reason()); return ResponseEntity.noContent().build();
    }
    @PostMapping(value = "/{id}/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EventResponse thumbnail(Principal actor, @PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
        try (var input = file.getInputStream()) {
            return events.uploadThumbnail(actorId(actor), id, input, file.getOriginalFilename(), file.getContentType(), file.getSize());
        }
    }
    @GetMapping("/{id}/registrations")
    public List<ParticipantResponse> participants(Principal actor, @PathVariable Long id) {
        return events.participants(actorId(actor), id);
    }
    @GetMapping("/{id}/waitlist")
    public List<OwnerWaitlistResponse> waitlist(Principal actor, @PathVariable Long id) {
        return events.ownerWaitlist(actorId(actor), id);
    }
    @GetMapping("/{id}/audit-logs")
    public List<AuditResponse> audit(Principal actor, @PathVariable Long id) {
        return events.auditHistory(actorId(actor), id);
    }
    @DeleteMapping("/{id}/registrations/{registrationId}")
    public ResponseEntity<Void> remove(Principal actor, @PathVariable Long id, @PathVariable Long registrationId,
                                       @Valid @RequestBody ReasonRequest request) {
        events.removeParticipant(actorId(actor), id, registrationId, request.reason());
        return ResponseEntity.noContent().build();
    }
    private Long actorId(Principal actor) { return Long.valueOf(actor.getName()); }
}

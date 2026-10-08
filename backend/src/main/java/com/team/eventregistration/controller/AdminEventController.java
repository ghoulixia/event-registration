package com.team.eventregistration.controller;

import com.team.eventregistration.dto.CreateEventRequest;
import com.team.eventregistration.dto.EventResponse;
import com.team.eventregistration.dto.UpdateEventRequest;
import com.team.eventregistration.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/events")
@Tag(name = "Events (Admin)", description = "Manage events")
public class AdminEventController {

    private final EventService eventService;

    public AdminEventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @Operation(summary = "Create event", description = "Creates a new event with OPEN status.")
    @ApiResponse(responseCode = "201", description = "Event created")
    @ApiResponse(responseCode = "400", description = "Validation error")
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createEvent(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update event", description = "Update event details.")
    @ApiResponse(responseCode = "200", description = "Event updated")
    @ApiResponse(responseCode = "404", description = "Event not found")
    public ResponseEntity<EventResponse> updateEvent(@PathVariable Long id,
                                                     @Valid @RequestBody UpdateEventRequest request) {
        return ResponseEntity.ok(eventService.updateEvent(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Toggle event status", description = "Change status between OPEN and CLOSED.")
    @ApiResponse(responseCode = "200", description = "Status updated")
    public ResponseEntity<EventResponse> updateStatus(@PathVariable Long id,
                                                      @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(eventService.updateStatus(id, body.getOrDefault("status", "")));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete event", description = "Marks the event as deleted and closes registration.")
    @ApiResponse(responseCode = "204", description = "Event deleted")
    @ApiResponse(responseCode = "404", description = "Event not found")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id) {
        eventService.softDeleteEvent(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload thumbnail", description = "Accepts JPG, PNG, WebP (max 5MB).")
    @ApiResponse(responseCode = "200", description = "Thumbnail uploaded")
    @ApiResponse(responseCode = "400", description = "Invalid file")
    public ResponseEntity<EventResponse> uploadThumbnail(@PathVariable Long id,
                                                         @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(eventService.uploadThumbnail(
                id, file.getInputStream(), file.getOriginalFilename(),
                file.getContentType(), file.getSize()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get event (Admin)", description = "Includes soft-deleted events.")
    @ApiResponse(responseCode = "200", description = "Event details")
    public ResponseEntity<EventResponse> getEventDetailForAdmin(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEventDetailForAdmin(id));
    }
}

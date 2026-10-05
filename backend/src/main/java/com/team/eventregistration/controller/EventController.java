package com.team.eventregistration.controller;

import com.team.eventregistration.dto.EventResponse;
import com.team.eventregistration.dto.EventSearchRequest;
import com.team.eventregistration.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Events (Public)", description = "View and search events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    @Operation(summary = "List events", description = "Filter by keyword, city, category with pagination.")
    @ApiResponse(responseCode = "200", description = "Paginated list of events")
    public ResponseEntity<Page<EventResponse>> getEvents(@ModelAttribute EventSearchRequest request) {
        Page<EventResponse> result = eventService.filterEvents(
                request.getKeyword(), request.getCity(), request.getCategory(),
                request.getPage(), request.getSize(), request.getSort());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get event details", description = "Retrieve event by ID including real-time availability.")
    @ApiResponse(responseCode = "200", description = "Event details")
    @ApiResponse(responseCode = "404", description = "Event not found")
    public ResponseEntity<EventResponse> getEventDetail(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEventDetail(id));
    }
}

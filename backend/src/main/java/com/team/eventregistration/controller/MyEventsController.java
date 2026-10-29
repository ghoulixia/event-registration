package com.team.eventregistration.controller;

import com.team.eventregistration.dto.EventResponse;
import com.team.eventregistration.service.EventService;
import com.team.eventregistration.dto.PageResult;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/api/me/events")
@SecurityRequirement(name = "bearerAuth")
public class MyEventsController {
    private final EventService events;
    public MyEventsController(EventService events) { this.events = events; }
    @GetMapping
    public PageResult<EventResponse> mine(Principal actor, @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "12") int size) {
        return events.myEvents(Long.valueOf(actor.getName()), page, size);
    }
    @GetMapping("/{id}")
    public EventResponse detail(Principal actor, @PathVariable Long id) {
        return events.getOwnedEvent(Long.valueOf(actor.getName()), id);
    }
}

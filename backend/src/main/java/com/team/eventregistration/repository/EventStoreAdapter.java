package com.team.eventregistration.repository;

import com.team.eventregistration.entity.*;
import com.team.eventregistration.service.port.EventStore;
import com.team.eventregistration.service.port.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class EventStoreAdapter implements EventStore {
    private final EventRepository events;
    public EventStoreAdapter(EventRepository events) { this.events = events; }
    public Optional<Event> find(Long id) { return events.findById(id); }
    public Optional<Event> lock(Long id) { return events.findByIdForUpdate(id); }
    public Event save(Event event) { return events.save(event); }
    public PageResult<Event> search(String keyword, City city, Category category, int page, int size, String sort) {
        return result(events.findByFilters(keyword, city, category,
                PageRequest.of(page, size, Sort.by(sort).ascending().and(Sort.by("id")))));
    }
    public PageResult<Event> owned(Long ownerId, int page, int size) {
        return result(events.findByOrganizerUserIdOrderByCreatedAtDescIdDesc(ownerId, PageRequest.of(page, size)));
    }
    private PageResult<Event> result(Page<Event> page) {
        return new PageResult<>(page.getContent(), page.getTotalElements(), page.getNumber(), page.getSize());
    }
}

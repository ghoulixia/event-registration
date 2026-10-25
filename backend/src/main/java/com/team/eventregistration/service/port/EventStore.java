package com.team.eventregistration.service.port;

import com.team.eventregistration.entity.Category;
import com.team.eventregistration.entity.City;
import com.team.eventregistration.entity.Event;
import java.util.Optional;

public interface EventStore {
    Optional<Event> find(Long id);
    Optional<Event> lock(Long id);
    Event save(Event event);
    PageResult<Event> search(String keyword, City city, Category category, int page, int size, String sort);
    PageResult<Event> owned(Long ownerId, int page, int size);
}

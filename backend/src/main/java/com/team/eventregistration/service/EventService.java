package com.team.eventregistration.service;

import com.team.eventregistration.dto.CreateEventRequest;
import com.team.eventregistration.dto.EventResponse;
import com.team.eventregistration.dto.UpdateEventRequest;
import com.team.eventregistration.entity.Category;
import com.team.eventregistration.entity.City;
import com.team.eventregistration.entity.Event;
import com.team.eventregistration.entity.EventStatus;
import com.team.eventregistration.exception.BusinessRuleException;
import com.team.eventregistration.exception.ResourceNotFoundException;
import com.team.eventregistration.repository.EventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final ImageStorageService imageStorageService;
    private final String thumbnailBaseUrl;

    public EventService(EventRepository eventRepository,
                        ImageStorageService imageStorageService,
                        @Value("${app.upload.base-url:/api/uploads/thumbnails/}") String thumbnailBaseUrl) {
        this.eventRepository = eventRepository;
        this.imageStorageService = imageStorageService;
        this.thumbnailBaseUrl = thumbnailBaseUrl;
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> filterEvents(String keyword, String cityStr,
                                            String categoryStr, int page,
                                            int size, String sortBy) {
        City city = parseEnum(City.class, cityStr);
        Category category = parseEnum(Category.class, categoryStr);
        Pageable pageable = PageRequest.of(page, Math.min(size, 50),
                Sort.by(Sort.Direction.ASC, resolveSortField(sortBy)));

        return eventRepository.findByFilters(keyword, city, category, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public EventResponse getEventDetail(Long eventId) {
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));
        return toResponse(event);
    }

    @Transactional(readOnly = true)
    public EventResponse getEventDetailForAdmin(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));
        return toResponse(event);
    }

    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        City city = requireEnum(City.class, request.getCity(), "City");
        Category category = requireEnum(Category.class, request.getCategory(), "Category");
        validateTimeRange(request.getStartTime(), request.getEndTime());

        Event event = new Event();
        event.setTitle(request.getTitle().trim());
        event.setDescription(request.getDescription());
        event.setOrganizerName(request.getOrganizerName().trim());
        event.setCity(city);
        event.setCategory(category);
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        event.setCapacity(request.getCapacity());
        event.setStatus(EventStatus.OPEN);
        event.setRegisteredCount(0);

        return toResponse(eventRepository.save(event));
    }

    @Transactional
    public EventResponse updateEvent(Long eventId, UpdateEventRequest request) {
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        City city = requireEnum(City.class, request.getCity(), "City");
        Category category = requireEnum(Category.class, request.getCategory(), "Category");
        validateTimeRange(request.getStartTime(), request.getEndTime());

        if (event.getStatus() == EventStatus.OPEN
                && request.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException("Cannot set start time in the past while event is open.");
        }

        if (request.getCapacity() < event.getRegisteredCount()) {
            throw new BusinessRuleException(
                    "New capacity (" + request.getCapacity()
                  + ") cannot be less than registered count ("
                  + event.getRegisteredCount() + ").");
        }

        event.setTitle(request.getTitle().trim());
        event.setDescription(request.getDescription());
        event.setOrganizerName(request.getOrganizerName().trim());
        event.setCity(city);
        event.setCategory(category);
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        event.setCapacity(request.getCapacity());

        return toResponse(eventRepository.save(event));
    }

    @Transactional
    public EventResponse updateStatus(Long eventId, String statusStr) {
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        EventStatus newStatus = requireEnum(EventStatus.class, statusStr, "Status");
        event.setStatus(newStatus);
        return toResponse(eventRepository.save(event));
    }

    @Transactional
    public void softDeleteEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        if (event.getDeletedAt() != null) return;

        event.setDeletedAt(LocalDateTime.now());
        event.setStatus(EventStatus.CLOSED);
        eventRepository.save(event);
    }

    @Transactional
    public EventResponse uploadThumbnail(Long eventId, InputStream inputStream,
                                         String originalName, String contentType,
                                         long fileSize) {
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        if (event.getThumbnailPath() != null) {
            imageStorageService.delete(event.getThumbnailPath());
        }

        String storedFileName = imageStorageService.store(inputStream, originalName, contentType, fileSize);
        event.setThumbnailPath(storedFileName);
        return toResponse(eventRepository.save(event));
    }

    // Active business computation — avoids Architecture Sinkhole
    private EventResponse toResponse(Event event) {
        EventResponse r = new EventResponse();
        r.setId(event.getId());
        r.setTitle(event.getTitle());
        r.setDescription(event.getDescription());
        r.setOrganizerName(event.getOrganizerName());
        r.setCity(event.getCity().name());
        r.setCategory(event.getCategory().name());
        r.setStartTime(event.getStartTime());
        r.setEndTime(event.getEndTime());
        r.setCapacity(event.getCapacity());
        r.setRegisteredCount(event.getRegisteredCount());
        r.setCreatedAt(event.getCreatedAt());
        r.setUpdatedAt(event.getUpdatedAt());

        boolean closed = event.getStatus() == EventStatus.CLOSED
                       || !LocalDateTime.now().isBefore(event.getStartTime());
        r.setEffectiveStatus(closed ? "CLOSED" : "OPEN");

        int remaining = event.getCapacity() - event.getRegisteredCount();
        r.setRemainingSlots(remaining);
        r.setCanRegister(!closed && remaining > 0 && event.getDeletedAt() == null);

        if (event.getThumbnailPath() != null) {
            r.setThumbnailUrl(thumbnailBaseUrl + event.getThumbnailPath());
        }

        return r;
    }

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new BusinessRuleException("End time must be after start time.");
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(enumClass, value.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private <E extends Enum<E>> E requireEnum(Class<E> enumClass, String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(fieldName + " is required.");
        }
        try {
            return Enum.valueOf(enumClass, value.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(fieldName + " is invalid: '" + value
                    + "'. Allowed: " + java.util.Arrays.toString(enumClass.getEnumConstants()));
        }
    }

    private String resolveSortField(String sortBy) {
        if (sortBy == null) return "startTime";
        return switch (sortBy.toLowerCase()) {
            case "title" -> "title";
            case "createdat", "created_at" -> "createdAt";
            case "capacity" -> "capacity";
            default -> "startTime";
        };
    }
}

package com.team.eventregistration.service;

import com.team.eventregistration.dto.*;
import com.team.eventregistration.entity.*;
import com.team.eventregistration.exception.*;
import com.team.eventregistration.repository.EventRepository;
import com.team.eventregistration.repository.UserRepository;
import com.team.eventregistration.service.port.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class EventService {
    private final EventRepository events;
    private final UserRepository users;
    private final ImageStorageService images;
    private final EventParticipation participation;
    private final AuditLogService audits;
    private final Clock clock;
    private final String thumbnailBaseUrl;

    public EventService(EventRepository events, UserRepository users, ImageStorageService images,
                        EventParticipation participation, AuditLogService audits, Clock clock,
                        @Value("${app.upload.base-url:/api/uploads/thumbnails/}") String thumbnailBaseUrl) {
        this.events = events;
        this.users = users;
        this.images = images;
        this.participation = participation;
        this.audits = audits;
        this.clock = clock;
        this.thumbnailBaseUrl = thumbnailBaseUrl;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<EventResponse> filterEvents(String keyword, String city, String category,
                                                  int page, int size, String sort) {
        validatePage(page, size);
        return events.search(keyword == null || keyword.isBlank() ? null : keyword.trim(),
                optionalEnum(City.class, city), optionalEnum(Category.class, category),
                page, Math.min(size, 50), sortField(sort)).map(this::toResponse);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public EventResponse getEventDetail(Long eventId) {
        Event event = events.findById(eventId).filter(e -> e.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found."));
        return toResponse(event);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public EventResponse getOwnedEvent(Long actorId, Long eventId) {
        Event event = events.findById(eventId).orElseThrow(() -> new ResourceNotFoundException("Event not found."));
        requireOwner(actorId, event);
        return toResponse(event);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<EventResponse> myEvents(Long actorId, int page, int size) {
        requireUser(actorId);
        validatePage(page, size);
        return events.owned(actorId, page, Math.min(size, 50)).map(this::toResponse);
    }

    @Transactional
    public EventResponse createEvent(Long actorId, CreateEventRequest request) {
        requireUser(actorId);
        LocalDateTime now = now();
        validateTimeRange(request.getStartTime(), request.getEndTime());
        if (!request.getStartTime().isAfter(now)) {
            throw new EventConflictException("Start time must be in the future.");
        }
        Event event = new Event();
        event.setOrganizerUserId(actorId);
        apply(event, request);
        event.setStatus(EventStatus.OPEN);
        event.setRegisteredCount(0);
        event.setHeldCount(0);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        event = events.save(event);
        audits.recordEventAction(event.getId(), actorId, "EVENT_CREATE", null,
                Map.of("after", snapshot(event)));
        return toResponse(event);
    }

    @Transactional
    public EventResponse updateEvent(Long actorId, Long eventId, UpdateEventRequest request) {
        Event event = lockOwned(actorId, eventId, false);
        LocalDateTime now = now();
        validateTimeRange(request.getStartTime(), request.getEndTime());
        if (!request.getStartTime().equals(event.getStartTime())) {
            if (!now.isBefore(event.getStartTime())) {
                throw new EventConflictException("A started event cannot change its start time.");
            }
            if (!request.getStartTime().isAfter(now)) {
                throw new EventConflictException("Start time must be in the future.");
            }
            participation.latestPendingExpiry(event).ifPresent(deadline -> {
                if (request.getStartTime().isBefore(deadline)) {
                    throw new EventConflictException("Close registration before moving the event earlier than a pending offer deadline.");
                }
            });
        }
        if ((long) request.getCapacity() < (long) event.getRegisteredCount() + event.getHeldCount()) {
            throw new EventConflictException("Capacity cannot be below confirmed registrations and pending offers.");
        }
        Map<String, Object> before = snapshot(event);
        int oldCapacity = event.getCapacity();
        apply(event, request);
        event.setUpdatedAt(now);
        if (event.getCapacity() > oldCapacity) {
            participation.afterCapacityIncrease(event, now);
        }
        events.save(event);
        audits.recordEventAction(eventId, actorId, "EVENT_UPDATE", null,
                Map.of("before", before, "after", snapshot(event)));
        return toResponse(event);
    }

    @Transactional
    public EventResponse updateStatus(Long actorId, Long eventId, String value) {
        Event event = lockOwned(actorId, eventId, false);
        LocalDateTime now = now();
        EventStatus status = requiredEnum(EventStatus.class, value, "Status");
        if (status == EventStatus.OPEN && !now.isBefore(event.getStartTime())) {
            throw new EventConflictException("A started event cannot reopen registration.");
        }
        if (status == event.getStatus()) {
            return toResponse(event);
        }
        Map<String, Object> before = snapshot(event);
        event.setStatus(status);
        event.setUpdatedAt(now);
        if (status == EventStatus.CLOSED) {
            participation.close(event, now);
        } else {
            participation.reopen(event, now);
        }
        events.save(event);
        audits.recordEventAction(eventId, actorId, status == EventStatus.OPEN ? "EVENT_OPEN" : "EVENT_CLOSE",
                null, Map.of("before", before, "after", snapshot(event)));
        return toResponse(event);
    }

    @Transactional
    public void softDeleteEvent(Long actorId, Long eventId, String reason) {
        requireReason(reason);
        Event event = lockOwned(actorId, eventId, true);
        if (event.getDeletedAt() != null) {
            return;
        }
        LocalDateTime now = now();
        Map<String, Object> before = snapshot(event);
        event.setDeletedAt(now);
        event.setStatus(EventStatus.CLOSED);
        event.setUpdatedAt(now);
        participation.delete(event, now);
        events.save(event);
        audits.recordEventAction(eventId, actorId, "EVENT_DELETE", reason.trim(),
                Map.of("before", before, "after", snapshot(event)));
    }

    @Transactional
    public EventResponse uploadThumbnail(Long actorId, Long eventId, InputStream input,
                                         String originalName, String contentType, long size) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Thumbnail updates require an active transaction.");
        }
        Event event = lockOwned(actorId, eventId, false);
        String previous = event.getThumbnailPath();
        String stored = images.store(input, originalName, contentType, size);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                images.delete(previous);
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    images.delete(stored);
                }
            }
        });
        event.setThumbnailPath(stored);
        event.setUpdatedAt(now());
        events.save(event);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("beforeThumbnail", previous);
        detail.put("afterThumbnail", stored);
        audits.recordEventAction(eventId, actorId, "EVENT_UPDATE", null, detail);
        return toResponse(event);
    }

    @Transactional(readOnly = true)
    public List<ParticipantResponse> participants(Long actorId, Long eventId) {
        Event event = requireOwned(actorId, eventId);
        return participation.participants(event);
    }

    @Transactional(readOnly = true)
    public List<OwnerWaitlistResponse> ownerWaitlist(Long actorId, Long eventId) {
        return participation.waitingList(requireOwned(actorId, eventId));
    }

    @Transactional(readOnly = true)
    public List<AuditResponse> auditHistory(Long actorId, Long eventId) {
        requireOwned(actorId, eventId);
        return audits.eventHistory(eventId);
    }

    @Transactional
    public void removeParticipant(Long actorId, Long eventId, Long registrationId, String reason) {
        requireReason(reason);
        Event event = lockOwned(actorId, eventId, false);
        LocalDateTime now = now();
        if (!now.isBefore(event.getStartTime())) {
            throw new EventConflictException("Participants cannot be removed after the event starts.");
        }
        ParticipantRemoval removal = participation.removeParticipant(event, registrationId, actorId, reason.trim(), now);
        if (removal.changed()) {
            event.setUpdatedAt(now);
            audits.recordEventAction(eventId, actorId, "PARTICIPANT_REMOVE", reason.trim(),
                    Map.of("beforeStatus", "CONFIRMED", "afterStatus", "REMOVED",
                            "registrationId", removal.registrationId(), "userId", removal.userId()));
        }
    }

    private Event requireOwned(Long actorId, Long eventId) {
        Event event = events.findById(eventId).orElseThrow(() -> new ResourceNotFoundException("Event not found."));
        requireOwner(actorId, event);
        return event;
    }
    private Event lockOwned(Long actorId, Long eventId, boolean includeDeleted) {
        Event event = events.findByIdForUpdate(eventId).orElseThrow(() -> new ResourceNotFoundException("Event not found."));
        requireOwner(actorId, event);
        if (!includeDeleted && event.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Event not found.");
        }
        return event;
    }
    private void requireOwner(Long actorId, Event event) {
        requireUser(actorId);
        if (event.getOrganizerUserId() == null || !event.getOrganizerUserId().equals(actorId)) {
            throw new AccessDeniedException("Only the event owner can perform this action.");
        }
    }
    private void requireUser(Long actorId) {
        if (actorId == null || users.findById(actorId).isEmpty()) {
            throw new AccessDeniedException("A valid user is required.");
        }
    }
    private void requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new IllegalArgumentException("A reason of 1 to 1000 characters is required.");
        }
    }
    private void validatePage(int page, int size) {
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException("Page must be nonnegative and size positive.");
        }
    }
    private void validateTimeRange(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Event end time must be after start time.");
        }
    }

    private void apply(Event event, CreateEventRequest request) {
        event.setTitle(request.getTitle().trim());
        event.setDescription(request.getDescription());
        event.setOrganizerName(request.getOrganizerName().trim());
        event.setCity(requiredEnum(City.class, request.getCity(), "City"));
        event.setCategory(requiredEnum(Category.class, request.getCategory(), "Category"));
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        event.setCapacity(request.getCapacity());
        event.setOfferTtlMinutes(request.getOfferTtlMinutes());
    }

    private void apply(Event event, UpdateEventRequest request) {
        event.setTitle(request.getTitle().trim());
        event.setDescription(request.getDescription());
        event.setOrganizerName(request.getOrganizerName().trim());
        event.setCity(requiredEnum(City.class, request.getCity(), "City"));
        event.setCategory(requiredEnum(Category.class, request.getCategory(), "Category"));
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        event.setCapacity(request.getCapacity());
        event.setOfferTtlMinutes(request.getOfferTtlMinutes());
    }

    private Map<String, Object> snapshot(Event event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", event.getTitle());
        data.put("description", event.getDescription());
        data.put("organizerName", event.getOrganizerName());
        data.put("city", event.getCity().name());
        data.put("category", event.getCategory().name());
        data.put("startTime", event.getStartTime().toString());
        data.put("endTime", event.getEndTime().toString());
        data.put("capacity", event.getCapacity());
        data.put("registeredCount", event.getRegisteredCount());
        data.put("heldCount", event.getHeldCount());
        data.put("status", event.getStatus().name());
        data.put("offerTtlMinutes", event.getOfferTtlMinutes());
        data.put("deletedAt", event.getDeletedAt() == null ? null : event.getDeletedAt().toString());
        return data;
    }
    private EventResponse toResponse(Event event) {
        EventResponse response = new EventResponse();
        response.setId(event.getId());
        response.setTitle(event.getTitle());
        response.setDescription(event.getDescription());
        response.setOrganizerName(event.getOrganizerName());
        response.setOrganizerUserId(event.getOrganizerUserId());
        response.setCity(event.getCity().name());
        response.setCategory(event.getCategory().name());
        response.setStartTime(event.getStartTime());
        response.setEndTime(event.getEndTime());
        response.setCapacity(event.getCapacity());
        response.setRegisteredCount(event.getRegisteredCount());
        response.setHeldCount(event.getHeldCount());
        response.setStatus(event.getStatus().name());
        response.setOfferTtlMinutes(event.getOfferTtlMinutes());
        response.setDeletedAt(event.getDeletedAt());
        response.setCreatedAt(event.getCreatedAt());
        response.setUpdatedAt(event.getUpdatedAt());
        boolean closed = event.getDeletedAt() != null || event.getStatus() == EventStatus.CLOSED || !now().isBefore(event.getStartTime());
        response.setEffectiveStatus(closed ? "CLOSED" : "OPEN");
        int remaining = event.getCapacity() - event.getRegisteredCount() - event.getHeldCount();
        response.setRemainingSlots(remaining);
        response.setCanRegister(!closed && remaining > 0 && remaining > participation.waitingCount(event));
        if (event.getThumbnailPath() != null) {
            response.setThumbnailUrl(thumbnailBaseUrl + event.getThumbnailPath());
        }
        return response;
    }
    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
    private <E extends Enum<E>> E optionalEnum(Class<E> type, String value) {
        return value == null || value.isBlank() ? null : requiredEnum(type, value, type.getSimpleName());
    }
    private <E extends Enum<E>> E requiredEnum(Class<E> type, String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(field + " is invalid.");
        }
    }
    private String sortField(String value) {
        if (value == null) {
            return "startTime";
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "title" -> "title";
            case "createdat", "created_at" -> "createdAt";
            case "capacity" -> "capacity";
            default -> "startTime";
        };
    }
}

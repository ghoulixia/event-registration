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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock private EventRepository eventRepository;
    @Mock private ImageStorageService imageStorageService;

    private EventService eventService;
    private Event sampleEvent;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, imageStorageService, "/api/uploads/thumbnails/");

        sampleEvent = new Event();
        sampleEvent.setId(1L);
        sampleEvent.setTitle("AI Summit 2026");
        sampleEvent.setDescription("AI Conference");
        sampleEvent.setOrganizerName("Tech Corp");
        sampleEvent.setCity(City.HANOI);
        sampleEvent.setCategory(Category.TECHNOLOGY);
        sampleEvent.setStartTime(LocalDateTime.now().plusDays(30));
        sampleEvent.setEndTime(LocalDateTime.now().plusDays(30).plusHours(8));
        sampleEvent.setCapacity(200);
        sampleEvent.setRegisteredCount(45);
        sampleEvent.setStatus(EventStatus.OPEN);
        sampleEvent.setThumbnailPath("test-thumb.jpg");
    }

    @Nested
    @DisplayName("getEventDetail")
    class GetEventDetail {

        @Test
        @DisplayName("Returns computed fields correctly")
        void shouldReturnComputedFields() {
            when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sampleEvent));
            EventResponse r = eventService.getEventDetail(1L);

            assertEquals("OPEN", r.getEffectiveStatus());
            assertEquals(155, r.getRemainingSlots());
            assertTrue(r.getCanRegister());
            assertEquals("/api/uploads/thumbnails/test-thumb.jpg", r.getThumbnailUrl());
        }

        @Test
        @DisplayName("CLOSED when start time has passed")
        void shouldReturnClosedWhenPastStartTime() {
            sampleEvent.setStartTime(LocalDateTime.now().minusHours(1));
            when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sampleEvent));

            EventResponse r = eventService.getEventDetail(1L);
            assertEquals("CLOSED", r.getEffectiveStatus());
            assertFalse(r.getCanRegister());
        }

        @Test
        @DisplayName("CLOSED when status is CLOSED")
        void shouldReturnClosedWhenStatusClosed() {
            sampleEvent.setStatus(EventStatus.CLOSED);
            when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sampleEvent));

            assertEquals("CLOSED", eventService.getEventDetail(1L).getEffectiveStatus());
        }

        @Test
        @DisplayName("canRegister false when full")
        void shouldNotRegisterWhenFull() {
            sampleEvent.setRegisteredCount(200);
            when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sampleEvent));

            EventResponse r = eventService.getEventDetail(1L);
            assertEquals(0, r.getRemainingSlots());
            assertFalse(r.getCanRegister());
        }

        @Test
        @DisplayName("Throws when not found")
        void shouldThrowWhenNotFound() {
            when(eventRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> eventService.getEventDetail(99L));
        }
    }

    @Nested
    @DisplayName("filterEvents")
    class FilterEvents {

        @Test
        @DisplayName("Delegates with parsed enum filters")
        void shouldDelegate() {
            when(eventRepository.findByFilters(eq("AI"), eq(City.HANOI), eq(Category.TECHNOLOGY), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(sampleEvent)));

            Page<EventResponse> result = eventService.filterEvents("AI", "HANOI", "TECHNOLOGY", 0, 12, "startTime");
            assertEquals(1, result.getTotalElements());
        }
    }

    @Nested
    @DisplayName("createEvent")
    class CreateEvent {

        @Test
        @DisplayName("Creates with OPEN status and captures saved entity")
        void shouldCreate() {
            CreateEventRequest req = new CreateEventRequest();
            req.setTitle("New Event"); req.setOrganizerName("Org");
            req.setCity("HANOI"); req.setCategory("SPORTS");
            req.setStartTime(LocalDateTime.now().plusDays(7));
            req.setEndTime(LocalDateTime.now().plusDays(7).plusHours(3));
            req.setCapacity(100);

            when(eventRepository.save(any(Event.class))).thenAnswer(inv -> {
                Event e = inv.getArgument(0); e.setId(10L); return e;
            });

            EventResponse res = eventService.createEvent(req);
            assertNotNull(res);

            ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
            verify(eventRepository).save(captor.capture());
            Event saved = captor.getValue();
            assertEquals(EventStatus.OPEN, saved.getStatus());
            assertEquals(0, saved.getRegisteredCount());
            assertEquals("New Event", saved.getTitle());
            assertEquals(100, saved.getCapacity());
        }

        @Test
        @DisplayName("Throws when endTime <= startTime")
        void shouldThrowOnBadTime() {
            CreateEventRequest req = new CreateEventRequest();
            req.setTitle("Bad"); req.setOrganizerName("Org");
            req.setCity("HANOI"); req.setCategory("SPORTS");
            req.setStartTime(LocalDateTime.now().plusDays(7));
            req.setEndTime(LocalDateTime.now().plusDays(6));
            req.setCapacity(50);

            assertThrows(BusinessRuleException.class, () -> eventService.createEvent(req));
        }
    }

    @Nested
    @DisplayName("updateEvent")
    class UpdateEvent {

        @Test
        @DisplayName("Updates event successfully")
        void shouldUpdateSuccessfully() {
            when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sampleEvent));
            when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateEventRequest req = new UpdateEventRequest();
            req.setTitle("Updated Title"); req.setOrganizerName("New Org");
            req.setCity("HCMC"); req.setCategory("EDUCATION");
            req.setStartTime(LocalDateTime.now().plusDays(40));
            req.setEndTime(LocalDateTime.now().plusDays(40).plusHours(4));
            req.setCapacity(300);

            EventResponse res = eventService.updateEvent(1L, req);
            assertEquals("Updated Title", res.getTitle());
            assertEquals("HCMC", res.getCity());
            assertEquals("EDUCATION", res.getCategory());
            assertEquals(300, res.getCapacity());
        }

        @Test
        @DisplayName("Throws when reducing capacity below registered")
        void shouldThrowOnCapacityViolation() {
            when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sampleEvent));

            UpdateEventRequest req = new UpdateEventRequest();
            req.setTitle("U"); req.setOrganizerName("O");
            req.setCity("HANOI"); req.setCategory("TECHNOLOGY");
            req.setStartTime(LocalDateTime.now().plusDays(30));
            req.setEndTime(LocalDateTime.now().plusDays(30).plusHours(8));
            req.setCapacity(10);

            assertThrows(BusinessRuleException.class, () -> eventService.updateEvent(1L, req));
        }

        @Test
        @DisplayName("Throws when setting start time in past while open")
        void shouldThrowWhenSettingPastStartTimeWhileOpen() {
            when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sampleEvent));

            UpdateEventRequest req = new UpdateEventRequest();
            req.setTitle("U"); req.setOrganizerName("O");
            req.setCity("HANOI"); req.setCategory("TECHNOLOGY");
            req.setStartTime(LocalDateTime.now().minusDays(1));
            req.setEndTime(LocalDateTime.now().plusDays(1));
            req.setCapacity(200);

            assertThrows(BusinessRuleException.class, () -> eventService.updateEvent(1L, req));
        }
    }

    @Nested
    @DisplayName("updateStatus")
    class UpdateStatus {

        @Test
        @DisplayName("Updates status to CLOSED")
        void shouldUpdateStatus() {
            when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sampleEvent));
            when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

            EventResponse res = eventService.updateStatus(1L, "CLOSED");
            assertEquals("CLOSED", res.getEffectiveStatus());
            verify(eventRepository).save(sampleEvent);
        }

        @Test
        @DisplayName("Throws on invalid status string")
        void shouldThrowOnInvalidStatus() {
            when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sampleEvent));
            assertThrows(BusinessRuleException.class, () -> eventService.updateStatus(1L, "INVALID"));
        }
    }

    @Nested
    @DisplayName("uploadThumbnail")
    class UploadThumbnail {

        @Test
        @DisplayName("Deletes old thumbnail, stores new one and saves event")
        void shouldUploadThumbnail() {
            when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sampleEvent));
            when(imageStorageService.store(any(), eq("new.png"), eq("image/png"), eq(100L))).thenReturn("new-uuid.png");
            when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

            ByteArrayInputStream in = new ByteArrayInputStream(new byte[100]);
            EventResponse res = eventService.uploadThumbnail(1L, in, "new.png", "image/png", 100L);

            verify(imageStorageService).delete("test-thumb.jpg");
            verify(imageStorageService).store(any(), eq("new.png"), eq("image/png"), eq(100L));
            assertEquals("/api/uploads/thumbnails/new-uuid.png", res.getThumbnailUrl());
        }
    }

    @Nested
    @DisplayName("softDeleteEvent")
    class SoftDelete {

        @Test
        @DisplayName("Sets deletedAt and status to CLOSED")
        void shouldSoftDelete() {
            when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
            eventService.softDeleteEvent(1L);

            assertNotNull(sampleEvent.getDeletedAt());
            assertEquals(EventStatus.CLOSED, sampleEvent.getStatus());
            verify(eventRepository).save(sampleEvent);
        }

        @Test
        @DisplayName("Idempotent on already deleted")
        void shouldBeIdempotent() {
            sampleEvent.setDeletedAt(LocalDateTime.now().minusDays(1));
            when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));

            eventService.softDeleteEvent(1L);
            verify(eventRepository, never()).save(any());
        }
    }
}

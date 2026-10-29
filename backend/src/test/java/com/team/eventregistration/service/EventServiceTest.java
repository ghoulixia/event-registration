package com.team.eventregistration.service;

import com.team.eventregistration.dto.CreateEventRequest;
import com.team.eventregistration.dto.EventResponse;
import com.team.eventregistration.dto.ParticipantRemoval;
import com.team.eventregistration.dto.PageResult;
import com.team.eventregistration.dto.UpdateEventRequest;
import com.team.eventregistration.entity.*;
import com.team.eventregistration.exception.EventConflictException;
import com.team.eventregistration.exception.ResourceNotFoundException;
import com.team.eventregistration.repository.EventRepository;
import com.team.eventregistration.repository.UserRepository;
import com.team.eventregistration.service.port.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {
    @Mock EventRepository events;
    @Mock UserRepository users;
    @Mock ImageStorageService images;
    @Mock EventParticipation participation;
    @Mock AuditLogService audit;
    final Clock clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    EventService service;
    Event event;

    @BeforeEach
    void setUp() {
        service = new EventService(events, users, images, participation, audit, clock, "/api/uploads/thumbnails/");
        event = new Event();
        event.setId(1L);
        event.setOrganizerUserId(42L);
        event.setTitle("Workshop");
        event.setOrganizerName("Community");
        event.setCity(City.HANOI);
        event.setCategory(Category.EDUCATION);
        event.setStartTime(now().plusHours(2));
        event.setEndTime(now().plusHours(4));
        event.setCapacity(100);
        event.setRegisteredCount(40);
        event.setHeldCount(10);
        event.setThumbnailPath("old.png");
        User owner = new User("owner@example.com", "Owner", "hash", null, Role.USER);
        owner.setId(42L);
        User other = new User("other@example.com", "Other", "hash", null, Role.USER);
        other.setId(7L);
        lenient().when(users.findById(42L)).thenReturn(Optional.of(owner));
        lenient().when(users.findById(7L)).thenReturn(Optional.of(other));
        lenient().when(events.findById(1L)).thenReturn(Optional.of(event));
        lenient().when(events.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        lenient().when(events.save(any())).thenAnswer(invocation -> {
            Event saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(10L);
            return saved;
        });
    }

    LocalDateTime now() { return LocalDateTime.now(clock); }

    CreateEventRequest create() {
        CreateEventRequest request = new CreateEventRequest();
        request.setTitle("New event");
        request.setOrganizerName("Community");
        request.setCity("HANOI");
        request.setCategory("MUSIC");
        request.setStartTime(now().plusDays(1));
        request.setEndTime(now().plusDays(1).plusHours(3));
        request.setCapacity(100);
        request.setOfferTtlMinutes(15);
        return request;
    }

    UpdateEventRequest update() {
        UpdateEventRequest request = new UpdateEventRequest();
        request.setTitle("Updated event");
        request.setOrganizerName(event.getOrganizerName());
        request.setCity(event.getCity().name());
        request.setCategory(event.getCategory().name());
        request.setStartTime(event.getStartTime());
        request.setEndTime(event.getEndTime());
        request.setCapacity(event.getCapacity());
        request.setOfferTtlMinutes(event.getOfferTtlMinutes());
        return request;
    }

    @Test
    void availabilitySubtractsBothConfirmedAndHeldPlaces() {
        EventResponse response = service.getEventDetail(1L);
        assertEquals(50, response.getRemainingSlots());
        assertTrue(response.getCanRegister());
        when(participation.waitingCount(event)).thenReturn(50L);
        assertFalse(service.getEventDetail(1L).getCanRegister());
    }

    @Test
    void injectedClockClosesAvailabilityAtTheExactStart() {
        event.setStartTime(now());
        assertEquals("CLOSED", service.getEventDetail(1L).getEffectiveStatus());
        assertFalse(service.getEventDetail(1L).getCanRegister());
    }

    @Test
    void deletedEventIsHiddenFromPublicReads() {
        event.setDeletedAt(now());
        assertThrows(ResourceNotFoundException.class, () -> service.getEventDetail(1L));
        assertNotNull(service.getOwnedEvent(42L, 1L).getDeletedAt());
    }

    @Test
    void creationUsesTheAuthenticatedOwnerAndStartsWithEmptyInventory() {
        EventResponse response = service.createEvent(42L, create());
        ArgumentCaptor<Event> saved = ArgumentCaptor.forClass(Event.class);
        verify(events).save(saved.capture());
        assertEquals(42L, saved.getValue().getOrganizerUserId());
        assertEquals(0, saved.getValue().getRegisteredCount());
        assertEquals(0, saved.getValue().getHeldCount());
        assertEquals(EventStatus.OPEN, saved.getValue().getStatus());
        assertEquals(15, response.getOfferTtlMinutes());
        assertEquals(now(), response.getCreatedAt());
        verify(audit).recordEventAction(eq(10L), eq(42L), eq("EVENT_CREATE"), isNull(), anyMap());
    }

    @Test
    void requestValidationEnforcesTtlRangeAndNullability() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (Integer ttl : new Integer[]{null, 4, 31}) {
                CreateEventRequest create = create();
                create.setOfferTtlMinutes(ttl);
                UpdateEventRequest update = update();
                update.setOfferTtlMinutes(ttl);
                assertEquals(Set.of("offerTtlMinutes"), validator.validate(create).stream()
                        .map(violation -> violation.getPropertyPath().toString()).collect(Collectors.toSet()));
                assertEquals(Set.of("offerTtlMinutes"), validator.validate(update).stream()
                        .map(violation -> violation.getPropertyPath().toString()).collect(Collectors.toSet()));
            }
            for (int ttl : new int[]{5, 30}) {
                CreateEventRequest create = create();
                create.setOfferTtlMinutes(ttl);
                UpdateEventRequest update = update();
                update.setOfferTtlMinutes(ttl);
                assertTrue(validator.validate(create).isEmpty());
                assertTrue(validator.validate(update).isEmpty());
            }
        }
    }

    @Test
    void anotherUserCannotReadOwnerDataOrMutateTheEvent() {
        assertThrows(AccessDeniedException.class, () -> service.participants(7L, 1L));
        assertThrows(AccessDeniedException.class, () -> service.ownerWaitlist(7L, 1L));
        assertThrows(AccessDeniedException.class, () -> service.auditHistory(7L, 1L));
        assertThrows(AccessDeniedException.class, () -> service.updateEvent(7L, 1L, update()));
        assertThrows(AccessDeniedException.class, () -> service.updateStatus(7L, 1L, "CLOSED"));
        assertThrows(AccessDeniedException.class, () -> service.softDeleteEvent(7L, 1L, "Reason"));
        withTransactionSynchronization(() -> assertThrows(AccessDeniedException.class,
                () -> service.uploadThumbnail(7L, 1L,
                        new ByteArrayInputStream(new byte[]{1}), "new.png", "image/png", 1)));
        verifyNoInteractions(images, participation, audit);
    }

    @Test
    void legacyEventWithoutAnOwnerCannotBeClaimedByAUser() {
        event.setOrganizerUserId(null);
        assertThrows(AccessDeniedException.class, () -> service.updateEvent(42L, 1L, update()));
        assertThrows(AccessDeniedException.class, () -> service.getOwnedEvent(42L, 1L));
    }

    @Test
    void capacityCannotFallBelowConfirmedPlusHeldPlaces() {
        UpdateEventRequest request = update();
        request.setCapacity(49);
        assertThrows(EventConflictException.class, () -> service.updateEvent(42L, 1L, request));
        assertEquals(100, event.getCapacity());
        verify(events, never()).save(any());
    }

    @Test
    void occupancyComparisonDoesNotOverflowAnInteger() {
        event.setCapacity(Integer.MAX_VALUE);
        event.setRegisteredCount(Integer.MAX_VALUE);
        event.setHeldCount(1);
        assertThrows(EventConflictException.class, () -> service.updateEvent(42L, 1L, update()));
    }

    @Test
    void increasingCapacityCallsAllocationBeforeTheAuditWrite() {
        UpdateEventRequest request = update();
        request.setCapacity(120);
        service.updateEvent(42L, 1L, request);
        var order = inOrder(events, participation, audit);
        order.verify(events).findByIdForUpdate(1L);
        order.verify(participation).afterCapacityIncrease(event, now());
        order.verify(events).save(event);
        order.verify(audit).recordEventAction(eq(1L), eq(42L), eq("EVENT_UPDATE"), isNull(), anyMap());
    }

    @Test
    void updatingTtlDoesNotInvokeAllocation() {
        UpdateEventRequest request = update();
        request.setOfferTtlMinutes(30);
        service.updateEvent(42L, 1L, request);
        assertEquals(30, event.getOfferTtlMinutes());
        verify(participation, never()).latestPendingExpiry(any());
        verify(participation, never()).afterCapacityIncrease(any(), any());
    }

    @Test
    void startCannotMoveEarlierThanAnOutstandingOfferDeadline() {
        when(participation.latestPendingExpiry(event)).thenReturn(Optional.of(now().plusMinutes(20)));
        UpdateEventRequest request = update();
        request.setStartTime(now().plusMinutes(10));
        assertThrows(EventConflictException.class, () -> service.updateEvent(42L, 1L, request));
        assertEquals(now().plusHours(2), event.getStartTime());
    }

    @Test
    void startedEventCannotBeRescheduledButItsMetadataCanChange() {
        event.setStartTime(now().minusMinutes(1));
        UpdateEventRequest rescheduled = update();
        rescheduled.setStartTime(now().plusHours(1));
        assertThrows(EventConflictException.class, () -> service.updateEvent(42L, 1L, rescheduled));
        service.updateEvent(42L, 1L, update());
        assertEquals("Updated event", event.getTitle());
        assertEquals(now().minusMinutes(1), event.getStartTime());
    }

    @Test
    void reopeningChecksTimeAfterTheEventLockHasBeenAcquired() {
        MutableClock advancing = new MutableClock(clock.instant());
        EventService waitingService = new EventService(events, users, images, participation, audit,
                advancing, "/api/uploads/thumbnails/");
        event.setStatus(EventStatus.CLOSED);
        event.setStartTime(LocalDateTime.now(advancing).plusSeconds(1));
        when(events.findByIdForUpdate(1L)).thenAnswer(invocation -> {
            advancing.advance(Duration.ofSeconds(1));
            return Optional.of(event);
        });
        assertThrows(EventConflictException.class, () -> waitingService.updateStatus(42L, 1L, "OPEN"));
        assertEquals(EventStatus.CLOSED, event.getStatus());
        verify(participation, never()).reopen(any(), any());
    }

    @Test
    void closingPassesTheClosedStateToTheLifecycleHandler() {
        doAnswer(invocation -> {
            assertEquals(EventStatus.CLOSED, event.getStatus());
            return null;
        }).when(participation).close(event, now());
        service.updateStatus(42L, 1L, "CLOSED");
        verify(participation).close(event, now());
        verify(audit).recordEventAction(eq(1L), eq(42L), eq("EVENT_CLOSE"), isNull(), anyMap());
    }

    @Test
    void deletionRequiresAReasonAndRetainsTheEventRow() {
        assertThrows(IllegalArgumentException.class, () -> service.softDeleteEvent(42L, 1L, " "));
        assertNull(event.getDeletedAt());
        service.softDeleteEvent(42L, 1L, " Venue unavailable ");
        assertEquals(now(), event.getDeletedAt());
        assertEquals(EventStatus.CLOSED, event.getStatus());
        verify(participation).delete(event, now());
        verify(audit).recordEventAction(eq(1L), eq(42L), eq("EVENT_DELETE"), eq("Venue unavailable"), anyMap());
    }

    @Test
    void repeatingDeletionDoesNotWriteAnotherAuditEntry() {
        event.setDeletedAt(now());
        service.softDeleteEvent(42L, 1L, "Same request");
        verifyNoInteractions(participation, audit);
    }

    @Test
    void removingAConfirmedParticipantRecordsItsIdentity() {
        when(participation.removeParticipant(event, 5L, 42L, "Duplicate", now()))
                .thenReturn(new ParticipantRemoval(5L, 7L, true));
        service.removeParticipant(42L, 1L, 5L, "Duplicate");
        verify(audit).recordEventAction(eq(1L), eq(42L), eq("PARTICIPANT_REMOVE"), eq("Duplicate"),
                argThat(details -> details.get("registrationId").equals(5L)
                        && details.get("userId").equals(7L)
                        && details.get("afterStatus").equals("REMOVED")));
    }

    @Test
    void repeatingRemovalDoesNotRecordAnotherTransition() {
        when(participation.removeParticipant(event, 5L, 42L, "Duplicate", now()))
                .thenReturn(new ParticipantRemoval(5L, 7L, false));
        service.removeParticipant(42L, 1L, 5L, "Duplicate");
        verifyNoInteractions(audit);
    }

    @Test
    void participantCannotBeRemovedAtTheExactEventStart() {
        event.setStartTime(now());
        assertThrows(EventConflictException.class, () -> service.removeParticipant(42L, 1L, 5L, "Reason"));
        verifyNoInteractions(participation, audit);
    }

    @Test
    void failedStorageLeavesThePreviousThumbnailUntouched() {
        when(images.store(any(), anyString(), anyString(), anyLong())).thenThrow(new IllegalStateException("Storage unavailable"));
        withTransactionSynchronization(() -> {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> service.uploadThumbnail(42L, 1L,
                            new ByteArrayInputStream(new byte[]{1}), "new.png", "image/png", 1));
            assertEquals("Storage unavailable", failure.getMessage());
        });
        assertEquals("old.png", event.getThumbnailPath());
        verify(images).store(any(), eq("new.png"), eq("image/png"), eq(1L));
        verify(images, never()).delete("old.png");
    }

    @Test
    void thumbnailUploadRequiresATransactionBeforeLockingOrWritingAFile() {
        assertThrows(IllegalStateException.class, () -> service.uploadThumbnail(42L, 1L,
                new ByteArrayInputStream(new byte[]{1}), "new.png", "image/png", 1));
        verify(events, never()).findByIdForUpdate(anyLong());
        verifyNoInteractions(images, participation, audit);
        assertEquals("old.png", event.getThumbnailPath());
    }

    @Test
    void paginationUsesTheRepositoryAndCapsPageSize() {
        when(events.search(eq("work"), eq(City.HANOI), eq(Category.EDUCATION), eq(0), eq(50), eq("startTime")))
                .thenReturn(new PageResult<>(List.of(event), 1, 0, 50));
        var result = service.filterEvents(" work ", "HANOI", "EDUCATION", 0, 1000, "startTime");
        assertEquals(1, result.totalElements());
        assertEquals(50, result.size());
        assertEquals(1L, result.content().getFirst().getId());
    }

    void withTransactionSynchronization(Runnable action) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            action.run();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    static class MutableClock extends Clock {
        private Instant value;
        MutableClock(Instant value) { this.value = value; }
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Ho_Chi_Minh"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(value, zone); }
        @Override public Instant instant() { return value; }
        void advance(Duration duration) { value = value.plus(duration); }
    }
}

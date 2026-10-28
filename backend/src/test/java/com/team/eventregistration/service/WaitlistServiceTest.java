package com.team.eventregistration.service;

import com.team.eventregistration.dto.MyWaitlistResponse;
import com.team.eventregistration.dto.SlotOfferResponse;
import com.team.eventregistration.dto.WaitlistEntryResponse;
import com.team.eventregistration.entity.*;
import com.team.eventregistration.repository.*;
import com.team.eventregistration.service.port.EventOfferLifecycle;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WaitlistServiceTest {
    private WaitlistEntryRepository waitlistEntryRepository;
    private EventRepository eventRepository;
    private UserRepository userRepository;
    private SlotOfferRepository slotOfferRepository;
    private RegistrationRepository registrationRepository;
    private EventOfferLifecycle eventOfferLifecycle;
    private WaitlistService waitlistService;
    private Clock clock;
    private Event event;
    private User firstUser;
    private User secondUser;
    private User thirdUser;

    @BeforeEach
    void setUp() {
        waitlistEntryRepository = mock(WaitlistEntryRepository.class);
        eventRepository = mock(EventRepository.class);
        userRepository = mock(UserRepository.class);
        registrationRepository = mock(RegistrationRepository.class);
        slotOfferRepository = mock(SlotOfferRepository.class);
        eventOfferLifecycle = mock(EventOfferLifecycle.class);
        clock = Clock.systemDefaultZone();

        waitlistService = new WaitlistService(
                waitlistEntryRepository,
                eventRepository,
                userRepository,
                registrationRepository,
                slotOfferRepository,
                eventOfferLifecycle,
                clock
        );

        event = new Event();
        event.setId(10L);
        event.setTitle("Full event");
        firstUser = user(1L);
        secondUser = user(2L);
        thirdUser = user(3L);
    }

    @Test
    void returnsWaitingEntriesByJoinedAtAscending() {
        LocalDateTime firstJoined = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime secondJoined = firstJoined.plusMinutes(1);
        WaitlistEntry first = entry(20L, firstUser, firstJoined);
        WaitlistEntry second = entry(21L, secondUser, secondJoined);
        when(eventRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(event));
        when(waitlistEntryRepository.findByEvent_IdAndStatusOrderByJoinedAtAscIdAsc(
                10L, WaitlistEntryStatus.WAITING)).thenReturn(List.of(first, second));

        List<WaitlistEntryResponse> result = waitlistService.getEventWaitlist(10L);

        assertEquals(List.of(20L, 21L), result.stream().map(WaitlistEntryResponse::getId).toList());
        assertEquals(List.of(1, 2), result.stream().map(WaitlistEntryResponse::getPosition).toList());
        verify(waitlistEntryRepository).findByEvent_IdAndStatusOrderByJoinedAtAscIdAsc(
                10L, WaitlistEntryStatus.WAITING);
    }

    @Test
    void preservesIdAscendingOrderWhenJoinedAtIsEqual() {
        LocalDateTime joinedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        WaitlistEntry lowerId = entry(20L, firstUser, joinedAt);
        WaitlistEntry higherId = entry(21L, thirdUser, joinedAt);
        when(eventRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(event));
        when(waitlistEntryRepository.findByEvent_IdAndStatusOrderByJoinedAtAscIdAsc(
                10L, WaitlistEntryStatus.WAITING)).thenReturn(List.of(lowerId, higherId));

        List<WaitlistEntryResponse> result = waitlistService.getEventWaitlist(10L);

        assertEquals(List.of(20L, 21L), result.stream().map(WaitlistEntryResponse::getId).toList());
    }

    @Test
    void excludesLeftEntriesFromTheActiveFifoList() {
        LocalDateTime joinedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        WaitlistEntry waiting = entry(20L, firstUser, joinedAt);
        WaitlistEntry left = entry(21L, secondUser, joinedAt.plusMinutes(1));
        left.leave();
        when(eventRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(event));
        when(waitlistEntryRepository.findByEvent_IdAndStatusOrderByJoinedAtAscIdAsc(
                10L, WaitlistEntryStatus.WAITING)).thenReturn(List.of(waiting));

        List<WaitlistEntryResponse> result = waitlistService.getEventWaitlist(10L);

        assertEquals(List.of(20L), result.stream().map(WaitlistEntryResponse::getId).toList());
        assertEquals(List.of(1), result.stream().map(WaitlistEntryResponse::getPosition).toList());
        verify(waitlistEntryRepository).findByEvent_IdAndStatusOrderByJoinedAtAscIdAsc(
                10L, WaitlistEntryStatus.WAITING);
        verify(waitlistEntryRepository, never()).findByEvent_IdAndStatusOrderByJoinedAtAscIdAsc(
                10L, WaitlistEntryStatus.LEFT);
    }

    @Test
    void updatesPositionsAfterAUserLeavesTheWaitlist() {
        LocalDateTime joinedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        WaitlistEntry first = entry(20L, firstUser, joinedAt);
        WaitlistEntry second = entry(21L, secondUser, joinedAt.plusMinutes(1));
        WaitlistEntry third = entry(22L, thirdUser, joinedAt.plusMinutes(2));
        when(waitlistEntryRepository.findByUser_IdAndEvent_IdAndStatusIn(
                1L,
                10L,
                List.of(WaitlistEntryStatus.WAITING, WaitlistEntryStatus.OFFERED)))
                .thenReturn(Optional.of(first));
        when(eventRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(event));
        when(eventRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(event));
        when(waitlistEntryRepository.findByEvent_IdAndStatusOrderByJoinedAtAscIdAsc(
                10L, WaitlistEntryStatus.WAITING)).thenReturn(List.of(second, third));

        waitlistService.leaveWaitlist(1L, 10L);
        List<WaitlistEntryResponse> result = waitlistService.getEventWaitlist(10L);

        assertEquals(WaitlistEntryStatus.LEFT, first.getStatus());
        assertEquals(List.of(21L, 22L), result.stream().map(WaitlistEntryResponse::getId).toList());
        assertEquals(List.of(1, 2), result.stream().map(WaitlistEntryResponse::getPosition).toList());
        verify(waitlistEntryRepository).save(first);
        verify(waitlistEntryRepository).findByEvent_IdAndStatusOrderByJoinedAtAscIdAsc(
                10L, WaitlistEntryStatus.WAITING);
    }

    @Test
    void translatesOnlyTheActiveWaitlistUniqueConstraintViolation() {
        prepareFullEventForJoin();
        ConstraintViolationException cause = mock(ConstraintViolationException.class);
        when(cause.getConstraintName()).thenReturn("uk_waitlist_active_user_event");
        when(waitlistEntryRepository.saveAndFlush(any(WaitlistEntry.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate", cause));

        assertThrows(
                com.team.eventregistration.exception.DuplicateWaitlistEntryException.class,
                () -> waitlistService.joinWaitlist(1L, 10L)
        );
    }

    @Test
    void rethrowsUnrelatedDataIntegrityViolations() {
        prepareFullEventForJoin();
        ConstraintViolationException cause = mock(ConstraintViolationException.class);
        when(cause.getConstraintName()).thenReturn("chk_waitlist_status");
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("invalid status", cause);
        when(waitlistEntryRepository.saveAndFlush(any(WaitlistEntry.class)))
                .thenThrow(failure);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> waitlistService.joinWaitlist(1L, 10L)
        );
    }

    @Test
    void allowsJoiningWhenAllRemainingCapacityIsHeldByAnOffer() {
        event.setCapacity(2);
        event.setRegisteredCount(1);
        event.setHeldCount(1);
        event.setStatus(EventStatus.OPEN);
        event.setStartTime(LocalDateTime.now().plusHours(1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(firstUser));
        when(eventRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(event));
        when(registrationRepository.findByUser_IdAndEvent_Id(1L, 10L))
                .thenReturn(Optional.empty());
        when(waitlistEntryRepository.findByUser_IdAndEvent_IdAndStatus(
                1L, 10L, WaitlistEntryStatus.WAITING)).thenReturn(Optional.empty());
        when(waitlistEntryRepository.saveAndFlush(any(WaitlistEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(waitlistEntryRepository.findByEvent_IdAndStatusOrderByJoinedAtAscIdAsc(
                10L, WaitlistEntryStatus.WAITING)).thenReturn(List.of());

        WaitlistEntryResponse response = waitlistService.joinWaitlist(1L, 10L);

        assertEquals(WaitlistEntryStatus.WAITING, response.getStatus());
    }

    @Test
    void rejectsJoiningADeletedEvent() {
        event.setStatus(EventStatus.OPEN);
        event.setDeletedAt(LocalDateTime.now().minusMinutes(1));
        event.setStartTime(LocalDateTime.now().plusHours(1));
        event.setCapacity(1);
        event.setRegisteredCount(1);
        event.setHeldCount(0);
        when(userRepository.findById(1L)).thenReturn(Optional.of(firstUser));
        when(eventRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(event));

        assertThrows(
                com.team.eventregistration.exception.BusinessRuleException.class,
                () -> waitlistService.joinWaitlist(1L, 10L)
        );

        verify(waitlistEntryRepository, never()).saveAndFlush(any(WaitlistEntry.class));
    }

    @Test
    void returnsMyActiveEntriesWithWaitingPositionAndPendingOffer() {
        LocalDateTime waitingJoinedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime offeredJoinedAt = waitingJoinedAt.plusMinutes(1);
        WaitlistEntry waiting = entry(20L, firstUser, waitingJoinedAt);
        WaitlistEntry waitingAhead = entry(19L, secondUser, waitingJoinedAt.minusMinutes(1));
        WaitlistEntry offered = entry(21L, firstUser, offeredJoinedAt);
        offered.offer();

        SlotOffer pendingOffer = mock(SlotOffer.class);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 1, 1, 10, 10);
        when(pendingOffer.getId()).thenReturn(30L);
        when(pendingOffer.getStatus()).thenReturn(SlotOfferStatus.PENDING);
        when(pendingOffer.getExpiresAt()).thenReturn(expiresAt);

        when(userRepository.findById(1L)).thenReturn(Optional.of(firstUser));
        when(waitlistEntryRepository.findByUser_IdAndStatusInOrderByJoinedAtAscIdAsc(
                1L, List.of(WaitlistEntryStatus.WAITING, WaitlistEntryStatus.OFFERED)))
                .thenReturn(List.of(waiting, offered));
        when(waitlistEntryRepository.findByEvent_IdAndStatusOrderByJoinedAtAscIdAsc(
                10L, WaitlistEntryStatus.WAITING))
                .thenReturn(List.of(waitingAhead, waiting));
        when(slotOfferRepository.findByWaitlistEntry_IdAndStatus(
                21L, SlotOfferStatus.PENDING))
                .thenReturn(Optional.of(pendingOffer));

        MyWaitlistResponse result = waitlistService.getMyWaitlist(1L);

        assertEquals(2, result.getEntries().size());

        WaitlistEntryResponse waitingResponse = result.getEntries().get(0);
        assertEquals(20L, waitingResponse.getId());
        assertEquals(WaitlistEntryStatus.WAITING, waitingResponse.getStatus());
        assertEquals(2, waitingResponse.getPosition());
        assertNull(waitingResponse.getOffer());

        WaitlistEntryResponse offeredResponse = result.getEntries().get(1);
        assertEquals(21L, offeredResponse.getId());
        assertEquals(WaitlistEntryStatus.OFFERED, offeredResponse.getStatus());
        assertNull(offeredResponse.getPosition());
        SlotOfferResponse offerResponse = offeredResponse.getOffer();
        assertEquals(30L, offerResponse.getId());
        assertEquals(SlotOfferStatus.PENDING, offerResponse.getStatus());
        assertEquals(expiresAt.toInstant(ZoneOffset.UTC), offerResponse.getExpiresAt());
        assertNotNull(result.getServerNow());
    }

    private void prepareFullEventForJoin() {
        event.setStatus(EventStatus.OPEN);
        event.setStartTime(LocalDateTime.now().plusDays(1));
        event.setCapacity(1);
        event.setRegisteredCount(1);
        when(eventRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(event));
        when(userRepository.findById(1L)).thenReturn(Optional.of(firstUser));
        when(eventRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(event));
        when(registrationRepository.findByUser_IdAndEvent_Id(1L, 10L))
                .thenReturn(Optional.empty());
        when(waitlistEntryRepository.findByUser_IdAndEvent_IdAndStatus(
                1L, 10L, WaitlistEntryStatus.WAITING)).thenReturn(Optional.empty());
    }

    private User user(Long id) {
        User user = new User(id + "@test.com", "User " + id, "password", null, Role.USER);
        user.setId(id);
        return user;
    }

    private WaitlistEntry entry(Long id, User user, LocalDateTime joinedAt) {
        WaitlistEntry entry = new WaitlistEntry(user, event);
        entry.setId(id);
        entry.setJoinedAt(joinedAt);
        return entry;
    }
}

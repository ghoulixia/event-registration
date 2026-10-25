package com.team.eventregistration.service;

import com.team.eventregistration.entity.*;
import com.team.eventregistration.exception.EventFullException;
import com.team.eventregistration.repository.EventRepository;
import com.team.eventregistration.repository.RegistrationRepository;
import com.team.eventregistration.repository.SlotOfferRepository;
import com.team.eventregistration.repository.UserRepository;
import com.team.eventregistration.repository.WaitlistEntryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class SlotOfferServiceIntegrationTest {
    @Autowired private SlotOfferService slotOfferService;
    @Autowired private RegistrationService registrationService;
    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WaitlistEntryRepository waitlistEntryRepository;
    @Autowired private SlotOfferRepository slotOfferRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private EntityManager entityManager;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdEventIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long eventId : createdEventIds) {
            slotOfferRepository.deleteAll(slotOfferRepository.findByEvent_Id(eventId));
            registrationRepository.findByEvent_Id(eventId)
                    .forEach(registration -> registrationRepository.deleteById(registration.getId()));
            waitlistEntryRepository.findByEvent_Id(eventId)
                    .forEach(entry -> waitlistEntryRepository.deleteById(entry.getId()));
            eventRepository.deleteById(eventId);
        }
        for (Long userId : createdUserIds) {
            userRepository.deleteById(userId);
        }
        createdEventIds.clear();
        createdUserIds.clear();
    }

    @Test
    void acceptsOfferAndMovesHeldSlotToRegistration() {
        User user = saveUser("accept");
        Event event = saveEvent(LocalDateTime.now().plusHours(2), 1);
        WaitlistEntry entry = saveEntry(user, event);

        SlotOffer offer = slotOfferService.offerNextWaitingEntry(event.getId()).orElseThrow();
        slotOfferService.acceptOffer(user.getId(), offer.getId());
        entityManager.clear();

        Event savedEvent = eventRepository.findById(event.getId()).orElseThrow();
        assertEquals(1, savedEvent.getRegisteredCount());
        assertEquals(0, savedEvent.getHeldCount());
        assertEquals(savedEvent.getCapacity(),
                savedEvent.getRegisteredCount() + savedEvent.getHeldCount());
        assertEquals(RegistrationStatus.CONFIRMED,
                registrationRepository.findByUser_IdAndEvent_Id(user.getId(), event.getId())
                        .orElseThrow().getStatus());
        assertEquals(WaitlistEntryStatus.ACCEPTED,
                waitlistEntryRepository.findById(entry.getId()).orElseThrow().getStatus());
    }

    @Test
    void expiresOfferAndReleasesHeldSlot() {
        User user = saveUser("expire");
        Event event = saveEvent(LocalDateTime.now().plusHours(2), 1);
        saveEntry(user, event);

        SlotOffer offer = slotOfferService.offerNextWaitingEntry(event.getId()).orElseThrow();
        slotOfferService.expireOffers(offer.getExpiresAt().plusSeconds(1));
        entityManager.clear();

        SlotOffer savedOffer = slotOfferRepository.findById(offer.getId()).orElseThrow();
        Event savedEvent = eventRepository.findById(event.getId()).orElseThrow();
        assertEquals(SlotOfferStatus.EXPIRED, savedOffer.getStatus());
        assertEquals(0, savedEvent.getHeldCount());
        assertEquals(0, savedEvent.getRegisteredCount());
        assertEquals(0, savedEvent.getRegisteredCount() + savedEvent.getHeldCount());
    }

    @Test
    void declinesOfferAndOffersNextWaitingEntryInFifoOrder() {
        User firstUser = saveUser("decline-first");
        User secondUser = saveUser("decline-second");
        Event event = saveEvent(LocalDateTime.now().plusHours(2), 1);
        saveEntry(firstUser, event);
        saveEntry(secondUser, event);

        SlotOffer firstOffer = slotOfferService.offerNextWaitingEntry(event.getId()).orElseThrow();
        slotOfferService.declineOffer(firstUser.getId(), firstOffer.getId());
        entityManager.clear();

        assertEquals(SlotOfferStatus.DECLINED,
                slotOfferRepository.findById(firstOffer.getId()).orElseThrow().getStatus());
        assertEquals(WaitlistEntryStatus.LEFT,
                waitlistEntryRepository.findById(firstOffer.getWaitlistEntry().getId())
                        .orElseThrow().getStatus());
        SlotOffer secondOffer = slotOfferRepository
                .findByUser_IdAndEvent_IdAndStatus(
                        secondUser.getId(), event.getId(), SlotOfferStatus.PENDING)
                .orElseThrow();
        assertEquals(secondUser.getId(), secondOffer.getUser().getId());
    }

    @Test
    void directRegistrationCannotBypassWaitingEntryWithAvailableCapacity() {
        User waitingUser = saveUser("priority-waiting");
        User directUser = saveUser("priority-direct");
        Event event = saveEvent(LocalDateTime.now().plusHours(2), 1);
        saveEntry(waitingUser, event);

        assertThrows(EventFullException.class,
                () -> registrationService.registerForEvent(directUser.getId(), event.getId()));
        entityManager.clear();

        assertEquals(WaitlistEntryStatus.OFFERED,
                waitlistEntryRepository.findByEvent_Id(event.getId()).get(0).getStatus());
        assertEquals(1, eventRepository.findById(event.getId()).orElseThrow().getHeldCount());
        assertEquals(0, registrationRepository.findByEvent_Id(event.getId()).size());
    }

    @Test
    void eventStartExpiresAndRevokesPendingOffersAndCancelsWaitingEntries() {
        User offeredUser = saveUser("started-offered");
        User waitingUser = saveUser("started-waiting");
        Event event = saveEvent(LocalDateTime.now().minusSeconds(1), 2);
        WaitlistEntry offeredEntry = new WaitlistEntry(offeredUser, event);
        offeredEntry.offer();
        waitlistEntryRepository.saveAndFlush(offeredEntry);
        WaitlistEntry waitingEntry = saveEntry(waitingUser, event);
        SlotOffer offer = slotOfferRepository.saveAndFlush(new SlotOffer(
                offeredEntry, event, offeredUser, LocalDateTime.now().plusMinutes(5)));
        event.setHeldCount(1);
        eventRepository.saveAndFlush(event);

        slotOfferService.finishWaitlistAtEventStart(event.getId());
        entityManager.clear();

        assertEquals(SlotOfferStatus.REVOKED,
                slotOfferRepository.findById(offer.getId()).orElseThrow().getStatus());
        assertEquals(WaitlistEntryStatus.CANCELLED,
                waitlistEntryRepository.findById(offeredEntry.getId()).orElseThrow().getStatus());
        assertEquals(WaitlistEntryStatus.CANCELLED,
                waitlistEntryRepository.findById(waitingEntry.getId()).orElseThrow().getStatus());
        Event savedEvent = eventRepository.findById(event.getId()).orElseThrow();
        assertEquals(0, savedEvent.getHeldCount());
        assertEquals(0, savedEvent.getRegisteredCount() + savedEvent.getHeldCount());
    }

    @Test
    void schedulerFinishesStartedEventsWithoutExpiredOffersAndAcceptLosesRace() throws Exception {
        User user = saveUser("race");
        Event event = saveEvent(LocalDateTime.now().plusHours(1), 1);
        saveEntry(user, event);
        SlotOffer offer = slotOfferService.offerNextWaitingEntry(event.getId()).orElseThrow();
        event.setStartTime(LocalDateTime.now().minusSeconds(1));
        eventRepository.saveAndFlush(event);

        CountDownLatch ready = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> scheduler = executor.submit(() -> {
            ready.countDown();
            await(ready);
            slotOfferService.expireOffers(LocalDateTime.now());
        });
        Future<?> acceptance = executor.submit(() -> {
            ready.countDown();
            await(ready);
            assertThrows(RuntimeException.class,
                    () -> slotOfferService.acceptOffer(user.getId(), offer.getId()));
        });
        scheduler.get();
        acceptance.get();
        executor.shutdown();

        entityManager.clear();
        Event savedEvent = eventRepository.findById(event.getId()).orElseThrow();
        assertEquals(0, savedEvent.getHeldCount());
        assertEquals(0, savedEvent.getRegisteredCount());
        assertEquals(SlotOfferStatus.REVOKED,
                slotOfferRepository.findById(offer.getId()).orElseThrow().getStatus());
        assertEquals(0, savedEvent.getRegisteredCount() + savedEvent.getHeldCount());
    }

    private User saveUser(String suffix) {
        User user = userRepository.saveAndFlush(new User(
                "slot-offer-" + suffix + "-" + System.nanoTime() + "@test.com",
                "Slot Offer Test User", "password", null, Role.USER));
        createdUserIds.add(user.getId());
        return user;
    }

    private Event saveEvent(LocalDateTime startTime, int capacity) {
        Event event = new Event();
        event.setTitle("Slot offer integration test");
        event.setOrganizerName("Test Organizer");
        event.setCity(City.HANOI);
        event.setCategory(Category.TECHNOLOGY);
        event.setStartTime(startTime);
        event.setEndTime(startTime.plusHours(1));
        event.setCapacity(capacity);
        event.setRegisteredCount(0);
        event.setHeldCount(0);
        event.setStatus(EventStatus.OPEN);
        Event savedEvent = eventRepository.saveAndFlush(event);
        createdEventIds.add(savedEvent.getId());
        return savedEvent;
    }

    private WaitlistEntry saveEntry(User user, Event event) {
        return waitlistEntryRepository.saveAndFlush(new WaitlistEntry(user, event));
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test thread interrupted.", exception);
        }
    }
}

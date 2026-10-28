package com.team.eventregistration.repository;

import com.team.eventregistration.entity.Category;
import com.team.eventregistration.entity.City;
import com.team.eventregistration.entity.Event;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :eventId")
    Optional<Event> findByIdForUpdate(@Param("eventId") Long eventId);

    @Query("SELECT e FROM Event e WHERE e.deletedAt IS NULL "
            + "AND (CAST(:keyword AS text) IS NULL OR LOWER(e.title) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%'))) "
            + "AND (:city IS NULL OR e.city = :city) "
            + "AND (:category IS NULL OR e.category = :category)")
    Page<Event> findByFilters(
            @Param("keyword") String keyword,
            @Param("city") City city,
            @Param("category") Category category,
            Pageable pageable);

    long countByDeletedAtIsNull();

    Page<Event> findByOrganizerUserIdOrderByCreatedAtDescIdDesc(
            Long ownerId, Pageable pageable);

    @Modifying
    @Query("UPDATE Event e "
            + "SET e.registeredCount = e.registeredCount + 1 "
            + "WHERE e.id = :eventId "
            + "AND e.status = 'OPEN' "
            + "AND e.registeredCount + e.heldCount < e.capacity "
            + "AND e.deletedAt IS NULL")
    int incrementRegisteredCountIfCapacityAvailable(
            @Param("eventId") Long eventId);

    @Modifying
    @Query("UPDATE Event e SET e.registeredCount = e.registeredCount - 1 "
            + "WHERE e.id = :eventId AND e.registeredCount > 0")
    int decrementRegisteredCount(@Param("eventId") Long eventId);

    List<Event> findByStartTimeLessThanEqual(LocalDateTime startTime);

    @Query("SELECT DISTINCT e FROM Event e "
            + "JOIN WaitlistEntry w ON e.id = w.event.id "
            + "WHERE (e.status <> EventStatus.OPEN OR e.deletedAt IS NOT NULL) "
            + "AND w.status in (WaitlistEntryStatus.WAITING, WaitlistEntryStatus.OFFERED)")
    List<Event> findInactiveWithActiveWaitlistEntries();
}

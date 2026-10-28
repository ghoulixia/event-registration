package com.team.eventregistration.repository;

import com.team.eventregistration.entity.WaitlistEntry;
import com.team.eventregistration.entity.WaitlistEntryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {
    Optional<WaitlistEntry> findByUser_IdAndEvent_IdAndStatus(
            Long userId, Long eventId, WaitlistEntryStatus status);

    Optional<WaitlistEntry> findByUser_IdAndEvent_IdAndStatusIn(
            Long userId, Long eventId, Collection<WaitlistEntryStatus> statuses);

    List<WaitlistEntry> findByEvent_IdAndStatusOrderByJoinedAtAscIdAsc(
            Long eventId, WaitlistEntryStatus status);

    List<WaitlistEntry> findByUser_IdAndStatusOrderByJoinedAtAscIdAsc(
            Long userId, WaitlistEntryStatus status);

    List<WaitlistEntry> findByUser_IdAndStatusInOrderByJoinedAtAscIdAsc(
            Long userId, Collection<WaitlistEntryStatus> statuses);

    List<WaitlistEntry> findByEvent_Id(Long eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WaitlistEntry w WHERE w.id = :entryId")
    Optional<WaitlistEntry> findByIdForUpdate(
            @Param("entryId") Long entryId
    );
}

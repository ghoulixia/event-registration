package com.team.eventregistration.repository;

import com.team.eventregistration.entity.WaitlistEntry;
import com.team.eventregistration.entity.WaitlistEntryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {
    Optional<WaitlistEntry> findByUser_IdAndEvent_IdAndStatus(
            Long userId, Long eventId, WaitlistEntryStatus status);

    List<WaitlistEntry> findByEvent_IdAndStatusOrderByJoinedAtAscIdAsc(
            Long eventId, WaitlistEntryStatus status);

    List<WaitlistEntry> findByUser_IdAndStatusOrderByJoinedAtAscIdAsc(
            Long userId, WaitlistEntryStatus status);
}

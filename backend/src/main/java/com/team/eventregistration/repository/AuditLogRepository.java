package com.team.eventregistration.repository;

import com.team.eventregistration.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(String targetType, Long targetId);
    @Query(value = """
            SELECT a.* FROM audit_logs a
            JOIN registrations r ON r.event_id = a.target_id
                AND (a.details ->> 'registration_id' = CAST(r.id AS text)
                     OR a.details ->> 'registrationId' = CAST(r.id AS text))
            WHERE r.user_id = :userId AND a.target_type = 'EVENT'
                AND a.action IN ('REGISTRATION_CONFIRM', 'REGISTRATION_CANCEL', 'PARTICIPANT_REMOVE')
            ORDER BY a.created_at ASC, a.id ASC
            """, nativeQuery = true)
    List<AuditLog> findRegistrationHistoryForUser(@Param("userId") Long userId);


    Page<AuditLog> findByTargetTypeAndTargetId(String targetType, Long targetId, Pageable pageable);

    Page<AuditLog> findByTargetTypeAndTargetIdAndAction(String targetType, Long targetId, String action, Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM AuditLog a WHERE a.actorUserId = :userId")
    void deleteByActorUserId(@Param("userId") Long userId);
}

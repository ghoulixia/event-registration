package com.team.eventregistration.repository;

import com.team.eventregistration.entity.AuditLog;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(String targetType, Long targetId);
    Page<AuditLog> findByTargetTypeAndTargetId(String targetType, Long targetId, Pageable pageable);
    Page<AuditLog> findByTargetTypeAndTargetIdAndAction(String targetType, Long targetId, String action, Pageable pageable);
}

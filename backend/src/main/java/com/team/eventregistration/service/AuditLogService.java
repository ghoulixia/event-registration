package com.team.eventregistration.service;

import com.team.eventregistration.entity.AuditLog;
import com.team.eventregistration.repository.AuditLogRepository;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {
    private static final Set<String> ACTIONS = Set.of("EVENT_CREATE", "EVENT_UPDATE", "EVENT_OPEN",
            "EVENT_CLOSE", "EVENT_DELETE", "PARTICIPANT_REMOVE");
    private final AuditLogRepository logs;

    public AuditLogService(AuditLogRepository logs) { this.logs = logs; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordEventAction(Long eventId, Long actorId, String action, String reason) {
        if (!ACTIONS.contains(action)) {
            throw new IllegalArgumentException("Unsupported audit action");
        }
        logs.save(new AuditLog(eventId, actorId, action, reason));
    }
}

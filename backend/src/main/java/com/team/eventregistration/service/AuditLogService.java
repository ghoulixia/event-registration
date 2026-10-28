package com.team.eventregistration.service;

import com.team.eventregistration.dto.AuditResponse;
import com.team.eventregistration.entity.AuditLog;
import com.team.eventregistration.repository.AuditLogRepository;
import com.team.eventregistration.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {
    private static final Set<String> ACTIONS = Set.of(
            "EVENT_CREATE", "EVENT_UPDATE", "EVENT_OPEN",
            "EVENT_CLOSE", "EVENT_DELETE", "PARTICIPANT_REMOVE",
            "REGISTRATION_CONFIRM", "REGISTRATION_CANCEL"
            );
    private final AuditLogRepository logs;
    private final UserRepository users;

    public AuditLogService(AuditLogRepository logs, UserRepository users) {
        this.logs = logs;
        this.users = users;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordEventAction(Long eventId, Long actorId, String action, String reason) {
        recordEventAction(eventId, actorId, action, reason, Map.of());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordEventAction(Long eventId, Long actorId, String action, String reason, Map<String, Object> details) {
        if (!ACTIONS.contains(action)) throw new IllegalArgumentException("Unsupported audit action");
        logs.save(new AuditLog(eventId, actorId, action, reason, details));
    }

    @Transactional(readOnly = true)
    public List<AuditResponse> eventHistory(Long eventId) {
        return logs.findByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc("EVENT", eventId).stream()
                .map(log -> new AuditResponse(log.getId(), log.getAction(), log.getReason(), log.getCreatedAt(),
                        log.getActorUserId(), users.findById(log.getActorUserId())
                                .map(user -> user.getDisplayName()).orElse("Unknown user"), log.getDetails()))
                .toList();
    }
}

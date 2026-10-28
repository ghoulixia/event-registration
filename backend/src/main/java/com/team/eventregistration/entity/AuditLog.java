package com.team.eventregistration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.LinkedHashMap;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;
    @Column(nullable = false)
    private String action;
    @Column(name = "target_type", nullable = false)
    private String targetType;
    @Column(name = "target_id", nullable = false)
    private Long targetId;
    @Column(name = "reason")
    private String reason;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {}

    public AuditLog(Long eventId, Long actorId, String action, String reason) {
        this.targetId = eventId;
        this.actorUserId = actorId;
        this.action = action;
        this.targetType = "EVENT";
        this.reason = reason;
        this.details = Map.of();
    }

    public AuditLog(Long eventId, Long actorId, String action, String reason, Map<String, Object> details) {
        this(eventId, actorId, action, reason);
        this.details = new LinkedHashMap<>(details);
    }

    public Long getId() { return id; }
    public Long getActorUserId() { return actorUserId; }
    public Map<String, Object> getDetails() { return details == null ? Map.of() : new LinkedHashMap<>(details); }
    public String getAction() { return action; }
    public Long getTargetId() { return targetId; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}

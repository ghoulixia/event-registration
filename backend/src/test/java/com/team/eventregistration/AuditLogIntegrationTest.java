package com.team.eventregistration;

import com.team.eventregistration.entity.Role;
import com.team.eventregistration.entity.User;
import com.team.eventregistration.repository.AuditLogRepository;
import com.team.eventregistration.repository.UserRepository;
import com.team.eventregistration.service.AuditLogService;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "JWT_SECRET=dGVzdC1vbmx5LXNlY3JldC1mb3ItYXV0aC1pbnRlZ3JhdGlvbi10ZXN0cy0zMi1ieXRlcw==")
class AuditLogIntegrationTest extends PostgresTestSupport {
    @Autowired AuditLogService audit;
    @Autowired AuditLogRepository logs;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager manager;
    private Long eventId;
    private Long actorId;

    @AfterEach
    void cleanup() {
        if (eventId != null) logs.deleteAll(logs.findByTargetTypeAndTargetId("EVENT", eventId,
                org.springframework.data.domain.Pageable.unpaged()).getContent());
        if (actorId != null) users.deleteById(actorId);
    }

    @Test
    void auditWriteParticipatesInBusinessTransaction() {
        User actor = users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "Audit Actor", "unused",
                null, Role.USER));
        actorId = actor.getId();
        eventId = -Math.abs(System.nanoTime());
        long before = logs.count();
        TransactionTemplate transaction = new TransactionTemplate(manager);
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            audit.recordEventAction(eventId, actor.getId(), "EVENT_DELETE", "reason for deletion");
            logs.flush();
            throw new IllegalStateException("business mutation failed");
        }));
        assertEquals(before, logs.count(), "failed mutation must leave no audit entry");
        transaction.executeWithoutResult(status -> audit.recordEventAction(eventId, actor.getId(), "EVENT_DELETE", "reason for deletion"));
        var page = logs.findByTargetTypeAndTargetId("EVENT", eventId,
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertEquals("reason for deletion", page.getContent().getFirst().getReason());
    }
}

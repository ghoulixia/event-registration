package com.team.eventregistration.service;

import com.team.eventregistration.entity.AuditLog;
import com.team.eventregistration.repository.AuditLogRepository;
import com.team.eventregistration.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {
    @Mock private AuditLogRepository logs;
    @Mock private UserRepository users;

    @Test
    void acceptsAllSrsAuditActionsAndPreservesDetails() {
        AuditLogService service = new AuditLogService(logs, users);

        for (String action : new String[]{
                "EVENT_CREATE", "EVENT_UPDATE", "EVENT_OPEN", "EVENT_CLOSE",
                "EVENT_DELETE", "PARTICIPANT_REMOVE",
                "REGISTRATION_CONFIRM", "REGISTRATION_CANCEL"}) {
            service.recordEventAction(10L, 3L, action, "reason",
                    java.util.Map.of("registration_id", 4L));
        }

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(logs, times(8)).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .allMatch(log -> log.getTargetId().equals(10L)
                        && log.getActorUserId().equals(3L)
                        && log.getDetails().get("registration_id").equals(4L)));
    }

    @Test
    void rejectsUnknownAuditActionInsteadOfSilentlyWritingIt() {
        AuditLogService service = new AuditLogService(logs, users);

        assertThrows(IllegalArgumentException.class,
                () -> service.recordEventAction(10L, 3L, "UNKNOWN", null));
        verifyNoInteractions(logs);
    }
}

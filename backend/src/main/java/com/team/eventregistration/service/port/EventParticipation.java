package com.team.eventregistration.service.port;
import com.team.eventregistration.entity.Event;
import com.team.eventregistration.dto.ParticipantResponse;
import com.team.eventregistration.dto.OwnerWaitlistResponse;
import com.team.eventregistration.dto.ParticipantRemoval;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventParticipation {
    void afterCapacityIncrease(Event event, LocalDateTime now);
    void close(Event event, LocalDateTime now);
    void reopen(Event event, LocalDateTime now);
    void delete(Event event, LocalDateTime now);
    ParticipantRemoval removeParticipant(Event event, Long registrationId, Long actorId, String reason, LocalDateTime now);
    Optional<LocalDateTime> latestPendingExpiry(Event event);
    List<ParticipantResponse> participants(Event event);
    List<OwnerWaitlistResponse> waitingList(Event event);
    long waitingCount(Event event);
}

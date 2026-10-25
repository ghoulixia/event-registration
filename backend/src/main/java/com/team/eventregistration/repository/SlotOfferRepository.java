package com.team.eventregistration.repository;

import com.team.eventregistration.entity.SlotOffer;
import com.team.eventregistration.entity.SlotOfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SlotOfferRepository extends JpaRepository<SlotOffer, Long> {
    Optional<SlotOffer> findByIdAndUser_Id(Long id, Long userId);

    Optional<SlotOffer> findByWaitlistEntry_IdAndStatus(
            Long waitlistEntryId,
            SlotOfferStatus status
    );

    Optional<SlotOffer> findByUser_IdAndEvent_IdAndStatus(
            Long userId,
            Long eventId,
            SlotOfferStatus status
    );

    List<SlotOffer> findByStatusAndExpiresAtLessThanEqual(
            SlotOfferStatus status,
            LocalDateTime now
    );

    List<SlotOffer> findByEvent_IdAndStatus(
            Long eventId,
            SlotOfferStatus status
    );
}

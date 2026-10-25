package com.team.eventregistration.repository;

import com.team.eventregistration.entity.Registration;
import com.team.eventregistration.entity.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegistrationRepository extends JpaRepository<Registration, Long> {
    List<Registration> findByUser_Id(Long userId);

    List<Registration> findByEvent_Id(Long eventId);

    Optional<Registration> findByUser_IdAndEvent_Id(Long userId, Long eventId);

    boolean existsByUser_IdAndEvent_IdAndStatus(Long userId, Long eventId, RegistrationStatus status);

    List<Registration> findByStatus(RegistrationStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Registration r "
         + "SET r.status = RegistrationStatus.CANCELLED, "
         + "r.cancelledAt = CURRENT_TIMESTAMP "
         + "WHERE r.id = :registrationId "
         + "AND r.user.id = :userId "
         + "AND r.status = RegistrationStatus.CONFIRMED")
    int cancelConfirmedRegistration(
            @Param("registrationId") Long registrationId,
            @Param("userId") Long userId);

    @Query("SELECT r.status "
         + "FROM Registration r "
         + "WHERE r.id = :registrationId")
    Optional<RegistrationStatus> findStatusById(@Param("registrationId") Long registrationId);
}

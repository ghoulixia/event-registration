package com.team.eventregistration.repository;

import com.team.eventregistration.entity.Registration;
import com.team.eventregistration.entity.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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
}

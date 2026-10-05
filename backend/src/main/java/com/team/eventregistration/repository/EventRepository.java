package com.team.eventregistration.repository;

import com.team.eventregistration.entity.Category;
import com.team.eventregistration.entity.City;
import com.team.eventregistration.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByIdAndDeletedAtIsNull(Long id);

    @Query("SELECT e FROM Event e WHERE e.deletedAt IS NULL "
         + "AND (:keyword IS NULL OR LOWER(e.title) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
         + "AND (:city IS NULL OR e.city = :city) "
         + "AND (:category IS NULL OR e.category = :category)")
    Page<Event> findByFilters(
            @Param("keyword") String keyword,
            @Param("city") City city,
            @Param("category") Category category,
            Pageable pageable);

    long countByDeletedAtIsNull();
}

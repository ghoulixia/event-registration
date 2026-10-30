package com.team.eventregistration.repository;

import com.team.eventregistration.entity.SlotOffer;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class SlotOfferRepositoryImpl implements SlotOfferRepositoryCustom {
    private final EntityManager entityManager;

    public SlotOfferRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void refresh(SlotOffer offer) {
        entityManager.refresh(offer);
    }
}
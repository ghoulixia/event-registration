package com.team.eventregistration.repository;

import com.team.eventregistration.entity.WaitlistEntry;
import com.team.eventregistration.exception.DuplicateWaitlistEntryException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class WaitlistEntryPersistenceAdapter {
    private final WaitlistEntryRepository waitlistEntryRepository;

    public WaitlistEntryPersistenceAdapter(
            WaitlistEntryRepository waitlistEntryRepository) {
        this.waitlistEntryRepository = waitlistEntryRepository;
    }

    public WaitlistEntry saveNewEntry(WaitlistEntry entry) {
        try {
            return waitlistEntryRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateActiveEntry(ex)) {
                throw new DuplicateWaitlistEntryException(
                        "User is already waiting for this event.");
            }
            throw ex;
        }
    }

    private boolean isDuplicateActiveEntry(DataIntegrityViolationException ex) {
        Throwable cause = ex;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException cve) {
                return "uk_waitlist_active_user_event"
                        .equals(cve.getConstraintName());
            }
            cause = cause.getCause();
        }

        return false;
    }
}
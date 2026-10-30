package com.team.eventregistration;

import com.team.eventregistration.entity.WaitlistEntry;
import com.team.eventregistration.exception.DuplicateWaitlistEntryException;
import com.team.eventregistration.repository.WaitlistEntryPersistenceAdapter;
import com.team.eventregistration.repository.WaitlistEntryRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaitlistEntryPersistenceAdapterTest {
    @Mock private WaitlistEntryRepository waitlistEntryRepository;
    @Mock private WaitlistEntry waitlistEntry;
    @InjectMocks private WaitlistEntryPersistenceAdapter adapter;

    @Test
    void translatesOnlyTheActiveWaitlistUniqueConstraintViolation() {
        ConstraintViolationException cause = mock(ConstraintViolationException.class);
        when(cause.getConstraintName()).thenReturn("uk_waitlist_active_user_event");
        when(waitlistEntryRepository.saveAndFlush(any(WaitlistEntry.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate", cause));

        assertThrows(
                DuplicateWaitlistEntryException.class,
                () -> adapter.saveNewEntry(waitlistEntry)
        );
    }

    @Test
    void rethrowsUnrelatedDataIntegrityViolations() {
        ConstraintViolationException cause = mock(ConstraintViolationException.class);
        when(cause.getConstraintName()).thenReturn("chk_waitlist_status");
        when(waitlistEntryRepository.saveAndFlush(any(WaitlistEntry.class)))
                .thenThrow(new DataIntegrityViolationException("invalid status", cause));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> adapter.saveNewEntry(waitlistEntry)
        );
    }
}

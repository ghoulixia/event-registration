ALTER TABLE waitlist_entries
    DROP CONSTRAINT chk_waitlist_status;

ALTER TABLE waitlist_entries
    ADD CONSTRAINT chk_waitlist_status
        CHECK (status IN (
            'WAITING',
            'OFFERED',
            'ACCEPTED',
            'LEFT',
            'SKIPPED',
            'CANCELLED'
        ));

ALTER TABLE events
    ADD COLUMN IF NOT EXISTS organizer_user_id BIGINT REFERENCES users (id);
CREATE INDEX idx_events_owner ON events (organizer_user_id, id);

ALTER TABLE registrations DROP CONSTRAINT chk_registrations_status;
ALTER TABLE registrations
    ADD CONSTRAINT chk_registrations_status
        CHECK (status IN ('CONFIRMED', 'CANCELLED', 'REMOVED'));
ALTER TABLE registrations
    ADD COLUMN removed_at TIMESTAMP;
ALTER TABLE registrations
    ADD COLUMN removed_reason VARCHAR(1000);
ALTER TABLE registrations
    ADD COLUMN removed_by_user_id BIGINT REFERENCES users (id);
ALTER TABLE registrations
    ADD CONSTRAINT chk_registration_removal
        CHECK ((status = 'REMOVED' AND removed_at IS NOT NULL AND removed_by_user_id IS NOT NULL
            AND removed_reason IS NOT NULL AND length(trim(removed_reason)) > 0)
            OR (status <> 'REMOVED' AND removed_at IS NULL AND removed_by_user_id IS NULL AND removed_reason IS NULL));

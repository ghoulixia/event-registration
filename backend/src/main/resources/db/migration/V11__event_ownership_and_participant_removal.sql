ALTER TABLE events ADD COLUMN organizer_user_id BIGINT REFERENCES users(id);
ALTER TABLE events ADD COLUMN IF NOT EXISTS held_count INT NOT NULL DEFAULT 0;
ALTER TABLE events ADD COLUMN IF NOT EXISTS offer_ttl_minutes INT NOT NULL DEFAULT 10;
ALTER TABLE events ADD CONSTRAINT chk_tv2_offer_ttl CHECK (offer_ttl_minutes BETWEEN 5 AND 30);
ALTER TABLE events ADD CONSTRAINT chk_tv2_event_occupancy
    CHECK (held_count >= 0 AND registered_count >= 0 AND registered_count::BIGINT + held_count <= capacity);
CREATE INDEX idx_events_owner ON events(organizer_user_id,id);

ALTER TABLE registrations DROP CONSTRAINT chk_registrations_status;
ALTER TABLE registrations ADD CONSTRAINT chk_registrations_status
    CHECK (status IN ('CONFIRMED','CANCELLED','REMOVED'));
ALTER TABLE registrations ADD COLUMN removed_at TIMESTAMP;
ALTER TABLE registrations ADD COLUMN removed_reason VARCHAR(1000);
ALTER TABLE registrations ADD COLUMN removed_by_user_id BIGINT REFERENCES users(id);
ALTER TABLE registrations ADD CONSTRAINT chk_registration_removal
    CHECK ((status = 'REMOVED' AND removed_at IS NOT NULL AND removed_by_user_id IS NOT NULL
            AND removed_reason IS NOT NULL AND length(trim(removed_reason)) > 0)
        OR (status <> 'REMOVED' AND removed_at IS NULL AND removed_by_user_id IS NULL AND removed_reason IS NULL));

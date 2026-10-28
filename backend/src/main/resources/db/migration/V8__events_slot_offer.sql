ALTER TABLE events
    ADD COLUMN IF NOT EXISTS held_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE events
    ADD COLUMN IF NOT EXISTS offer_ttl_minutes INTEGER NOT NULL DEFAULT 10;

ALTER TABLE events
    ADD CONSTRAINT chk_events_held_count
        CHECK (held_count >= 0);

ALTER TABLE events
    ADD CONSTRAINT chk_events_offer_ttl
        CHECK (offer_ttl_minutes BETWEEN 5 AND 30);

ALTER TABLE events
    ADD CONSTRAINT chk_events_total_held_registered
        CHECK (registered_count + held_count <= capacity);
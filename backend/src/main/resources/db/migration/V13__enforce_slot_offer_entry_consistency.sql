ALTER TABLE waitlist_entries
    ADD CONSTRAINT uk_waitlist_entry_id_user_event
        UNIQUE (id, user_id, event_id);

ALTER TABLE slot_offers
    ADD CONSTRAINT fk_slot_offer_entry_user_event
        FOREIGN KEY (waitlist_entry_id, user_id, event_id)
            REFERENCES waitlist_entries (id, user_id, event_id);
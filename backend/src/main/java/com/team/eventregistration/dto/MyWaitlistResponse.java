package com.team.eventregistration.dto;

import java.time.Instant;
import java.util.List;

public class MyWaitlistResponse {
    private List<WaitlistEntryResponse> entries;
    private Instant serverNow;

    public MyWaitlistResponse(
            List<WaitlistEntryResponse> entries,
            Instant serverNow
    ) {
        this.entries = entries;
        this.serverNow = serverNow;
    }

    public List<WaitlistEntryResponse> getEntries() {
        return entries;
    }

    public Instant getServerNow() {
        return serverNow;
    }
}

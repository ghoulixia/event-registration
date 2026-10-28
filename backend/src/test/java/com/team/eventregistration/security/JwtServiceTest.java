package com.team.eventregistration.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private static final String SECRET =
            "dGVzdC1vbmx5LXNlY3JldC1mb3ItaHR0cC1qd3QtdGVzdHMtMzItYnl0ZXM=";

    @Test
    void issuesAndVerifiesTokenForTheUserId() {
        JwtService jwt = new JwtService(SECRET, 60_000);

        String token = jwt.issue(42L);

        assertEquals(42L, jwt.verify(token));
    }

    @Test
    void rejectsTamperedAndMalformedTokens() {
        JwtService jwt = new JwtService(SECRET, 60_000);

        assertThrows(JwtException.class, () -> jwt.verify("not-a-token"));
        String token = jwt.issue(42L);
        assertThrows(JwtException.class, () -> jwt.verify(token + "tampered"));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService("c2hvcnQ=", 60_000));
        assertThrows(IllegalArgumentException.class, () -> new JwtService(SECRET, 0));
    }
}

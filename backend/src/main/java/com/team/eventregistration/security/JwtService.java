package com.team.eventregistration.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMs;
    private final Clock clock = Clock.systemUTC();

    public JwtService(@Value("${JWT_SECRET}") String secret,
                      @Value("${JWT_EXPIRATION_MS:3600000}") long expirationMs) {
        byte[] bytes = Base64.getDecoder().decode(secret);
        if (bytes.length < 32 || expirationMs <= 0) {
            throw new IllegalArgumentException("JWT configuration is invalid");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMs = expirationMs;
    }

    public String issue(Long userId) {
        long now = clock.millis();
        return Jwts.builder().subject(userId.toString())
                .issuedAt(new Date(now)).expiration(new Date(Math.addExact(now, expirationMs)))
                .signWith(key, Jwts.SIG.HS256).compact();
    }

    public Long verify(String token) {
        return Long.valueOf(Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().getSubject());
    }
}

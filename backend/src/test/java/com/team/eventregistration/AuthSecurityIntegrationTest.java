package com.team.eventregistration;

import com.team.eventregistration.entity.Role;
import com.team.eventregistration.entity.User;
import com.team.eventregistration.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "JWT_SECRET=dGVzdC1vbmx5LXNlY3JldC1mb3ItYXV0aC1pbnRlZ3JhdGlvbi10ZXN0cy0zMi1ieXRlcw==")
class AuthSecurityIntegrationTest {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @Value("${local.server.port}") int port;
    @Autowired UserRepository users;
    private String registeredEmail;

    @AfterEach
    void cleanup() {
        if (registeredEmail != null) users.findByEmail(registeredEmail).ifPresent(users::delete);
    }

    private HttpResponse<String> request(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void registrationLoginAndTokenBoundaries() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        registeredEmail = email;
        String password = " test-pass-123 ";
        String registration = "{\"email\":\" " + email.toUpperCase() + " \",\"displayName\":\"Test User\",\"password\":\""
                + password + "\",\"confirmPassword\":\"" + password + "\"}";
        HttpResponse<String> created = request("POST", "/api/auth/register", registration, null);
        assertEquals(201, created.statusCode(), created.body());
        assertTrue(created.body().contains(email));
        assertFalse(created.body().contains("passwordHash"));
        assertFalse(created.body().contains(password));
        User user = users.findByEmail(email).orElseThrow();
        assertNotEquals(password, user.getPasswordHash());
        assertTrue(user.getPasswordHash().startsWith("$2"));
        assertEquals(Role.USER, user.getRole());

        assertEquals(409, request("POST", "/api/auth/register", registration, null).statusCode());
        String mismatch = registration.replace("\"confirmPassword\":\"" + password + "\"", "\"confirmPassword\":\"different\"");
        assertEquals(400, request("POST", "/api/auth/register", mismatch, null).statusCode());
        String credentials = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        assertEquals(401, request("POST", "/api/auth/login", credentials.replace(password, "wrong-pass"), null).statusCode());
        HttpResponse<String> login = request("POST", "/api/auth/login", credentials, null);
        assertEquals(200, login.statusCode());
        assertFalse(login.body().contains("passwordHash"));
        Matcher match = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(login.body());
        assertTrue(match.find());
        String token = match.group(1);
        assertEquals(401, request("GET", "/api/users/me", null, null).statusCode());
        assertEquals(401, request("POST", "/api/admin/events", "{}", null).statusCode());
        assertEquals(200, request("GET", "/api/users/me", null, token).statusCode());
        assertEquals(401, request("GET", "/api/users/me", null, token + "tampered").statusCode());
        assertEquals(401, request("GET", "/api/users/me", null, "invalid").statusCode());
        byte[] secret = Base64.getDecoder().decode("dGVzdC1vbmx5LXNlY3JldC1mb3ItYXV0aC1pbnRlZ3JhdGlvbi10ZXN0cy0zMi1ieXRlcw==");
        String expired = Jwts.builder().subject(user.getId().toString()).issuedAt(new Date(1000))
                .expiration(new Date(2000)).signWith(Keys.hmacShaKeyFor(secret), Jwts.SIG.HS256).compact();
        assertEquals(401, request("GET", "/api/users/me", null, expired).statusCode());
    }
}

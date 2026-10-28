package com.team.eventregistration;

import com.team.eventregistration.entity.Role;
import com.team.eventregistration.entity.User;
import com.team.eventregistration.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "JWT_SECRET=dGVzdC1vbmx5LXNlY3JldC1mb3ItYXV0aC1pbnRlZ3JhdGlvbi10ZXN0cy0zMi1ieXRlcw==")
class AuthSecurityIntegrationTest extends PostgresTestSupport {
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    record ApiResult(int statusCode, String body) {}

    @BeforeEach void prepareHttp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @Autowired UserRepository users;
    private String registeredEmail;

    @AfterEach
    void cleanup() {
        if (registeredEmail != null) users.findByEmail(registeredEmail).ifPresent(users::delete);
    }

    private ApiResult request(String method, String path, String body, String token) throws Exception {
        var builder = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path).contentType("application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.content(body);
        var response = mvc.perform(builder).andReturn().getResponse();
        return new ApiResult(response.getStatus(), response.getContentAsString());
    }

    @Test
    void registrationLoginAndTokenBoundaries() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        registeredEmail = email;
        String password = " test-pass-123 ";
        String registration = "{\"email\":\" " + email.toUpperCase() + " \",\"displayName\":\"Test User\",\"password\":\""
                + password + "\",\"confirmPassword\":\"" + password + "\"}";
        ApiResult created = request("POST", "/api/auth/register", registration, null);
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
        ApiResult login = request("POST", "/api/auth/login", credentials, null);
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
        assertEquals(200, request("GET", "/api/events", null, expired).statusCode());
        assertEquals(200, request("POST", "/api/auth/login", credentials, expired).statusCode());
    }
}

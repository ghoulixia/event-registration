package com.team.eventregistration.service;

import com.team.eventregistration.dto.LoginRequest;
import com.team.eventregistration.dto.RegisterRequest;
import com.team.eventregistration.entity.Role;
import com.team.eventregistration.entity.User;
import com.team.eventregistration.repository.UserRepository;
import com.team.eventregistration.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock private UserRepository users;
    @Mock private JwtService jwt;

    private AuthService auth;
    private BCryptPasswordEncoder passwords;

    @BeforeEach
    void setUp() {
        passwords = new BCryptPasswordEncoder();
        auth = new AuthService(users, passwords, jwt);
    }

    @Test
    void registersNormalizedEmailAndNeverReturnsPasswordHash() {
        RegisterRequest request = new RegisterRequest(
                "  PERSON@EXAMPLE.COM ", "Person", "secret123", "secret123", "0123");
        User saved = new User("person@example.com", "Person", "encoded", "0123", Role.USER);
        when(users.existsByEmail("person@example.com")).thenReturn(false);
        when(users.saveAndFlush(any(User.class))).thenReturn(saved);

        var response = auth.register(request);

        assertEquals("person@example.com", response.email());
        assertEquals("Person", response.displayName());
        assertEquals("0123", response.phone());
        verify(users).saveAndFlush(argThat(user ->
                user.getEmail().equals("person@example.com")
                        && !user.getPasswordHash().equals("secret123")
                        && user.getRole() == Role.USER));
    }

    @Test
    void rejectsPasswordMismatchBeforeCheckingTheRepository() {
        RegisterRequest request = new RegisterRequest(
                "person@example.com", "Person", "secret123", "different", null);

        AuthFailure failure = assertThrows(AuthFailure.class, () -> auth.register(request));

        assertEquals(400, failure.status());
        verifyNoInteractions(users);
    }

    @Test
    void rejectsDuplicateEmail() {
        when(users.existsByEmail("person@example.com")).thenReturn(true);

        AuthFailure failure = assertThrows(AuthFailure.class, () -> auth.register(
                new RegisterRequest("person@example.com", "Person", "secret123", "secret123", null)));

        assertEquals(409, failure.status());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void loginNormalizesEmailAndIssuesBearerToken() {
        User user = new User("person@example.com", "Person",
                passwords.encode("secret123"), null, Role.USER);
        user.setId(7L);
        when(users.findByEmail("person@example.com")).thenReturn(Optional.of(user));
        when(jwt.issue(7L)).thenReturn("token");

        var response = auth.login(new LoginRequest(" PERSON@EXAMPLE.COM ", "secret123"));

        assertEquals("token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        verify(jwt).issue(7L);
    }

    @Test
    void rejectsUnknownUserAndWrongPasswordWithTheSameFailure() {
        when(users.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        AuthFailure missing = assertThrows(AuthFailure.class,
                () -> auth.login(new LoginRequest("missing@example.com", "wrong")));
        assertEquals(401, missing.status());

        User user = new User("person@example.com", "Person",
                passwords.encode("secret123"), null, Role.USER);
        when(users.findByEmail("person@example.com")).thenReturn(Optional.of(user));
        AuthFailure wrong = assertThrows(AuthFailure.class,
                () -> auth.login(new LoginRequest("person@example.com", "wrong")));
        assertEquals(401, wrong.status());
    }

    @Test
    void meReturnsOnlyThePublicUserProfile() {
        User user = new User("person@example.com", "Person", "hash", "0123", Role.USER);
        user.setId(9L);
        when(users.findById(9L)).thenReturn(Optional.of(user));

        var response = auth.me(9L);

        assertEquals(9L, response.id());
        assertEquals("person@example.com", response.email());
        assertEquals("Person", response.displayName());
        assertEquals("0123", response.phone());
    }
}

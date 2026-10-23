package com.team.eventregistration.service;

import com.team.eventregistration.dto.AuthResponse;
import com.team.eventregistration.dto.LoginRequest;
import com.team.eventregistration.dto.RegisterRequest;
import com.team.eventregistration.dto.UserResponse;
import com.team.eventregistration.entity.Role;
import com.team.eventregistration.entity.User;
import com.team.eventregistration.repository.UserRepository;
import com.team.eventregistration.security.JwtService;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder passwords, JwtService jwt) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
    }

    public UserResponse register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new AuthFailure(400, "VALIDATION_ERROR", "Passwords do not match");
        }
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw duplicate();
        }
        User saved = users.saveAndFlush(new User(email, request.displayName(),
                passwords.encode(request.password()), request.phone(), Role.USER));
        return profile(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email()))
                .orElseThrow(AuthService::invalidCredentials);
        if (!passwords.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return new AuthResponse(jwt.issue(user.getId()), "Bearer");
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return profile(users.findById(userId).orElseThrow(() ->
                new AuthFailure(401, "UNAUTHENTICATED", "Authentication required")));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static UserResponse profile(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getPhone());
    }

    private static AuthFailure invalidCredentials() {
        return new AuthFailure(401, "INVALID_CREDENTIALS", "Invalid email or password");
    }

    private static AuthFailure duplicate() {
        return new AuthFailure(409, "EMAIL_ALREADY_EXISTS", "Email already registered");
    }
}

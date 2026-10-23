package com.team.eventregistration.controller;

import com.team.eventregistration.dto.AuthResponse;
import com.team.eventregistration.dto.LoginRequest;
import com.team.eventregistration.dto.RegisterRequest;
import com.team.eventregistration.dto.UserResponse;
import com.team.eventregistration.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(201).body(auth.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }
}
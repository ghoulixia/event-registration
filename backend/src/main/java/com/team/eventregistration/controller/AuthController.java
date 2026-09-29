package com.team.eventregistration.controller;

import com.team.eventregistration.dto.LoginRequest;
import com.team.eventregistration.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/register")
    public ResponseEntity<String> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return ResponseEntity.ok("Register endpoint ready");
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok("Login endpoint ready");
    }
}
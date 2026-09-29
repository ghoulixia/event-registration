package com.team.eventregistration.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank
        @Email
        String email,

        @NotBlank
        String displayName,

        @NotBlank
        @Size(min = 6)
        String password,

        @NotBlank
        String confirmPassword,

        String phone
) {
}
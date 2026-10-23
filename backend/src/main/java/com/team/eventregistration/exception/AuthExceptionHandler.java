package com.team.eventregistration.exception;

import com.team.eventregistration.service.AuthFailure;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {
    public record Error(String code, String message, String traceId) {}

    @ExceptionHandler(AuthFailure.class)
    public ResponseEntity<Error> failure(AuthFailure ex) {
        return ResponseEntity.status(ex.status()).body(new Error(ex.code(), ex.getMessage(), UUID.randomUUID().toString()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Error> constraint(DataIntegrityViolationException ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())
                    && sql.getMessage() != null && sql.getMessage().contains("users_email_key")) {
                return failure(new AuthFailure(409, "EMAIL_ALREADY_EXISTS", "Email already registered"));
            }
            cause = cause.getCause();
        }
        return ResponseEntity.internalServerError().body(new Error("INTERNAL_ERROR", "An unexpected error occurred", UUID.randomUUID().toString()));
    }
}

package com.team.eventregistration.exception;

public class DuplicateWaitlistEntryException extends RuntimeException {
    public DuplicateWaitlistEntryException(String message) {
        super(message);
    }
}

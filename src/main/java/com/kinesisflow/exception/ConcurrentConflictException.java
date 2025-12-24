package com.kinesisflow.exception;

public class ConcurrentConflictException extends RuntimeException {
    public ConcurrentConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}

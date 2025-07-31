package org.example.kinesisflow.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<Map<String, Object>> buildErrorResponse(String message, HttpStatus status, Map<String, Object> additionalFields) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", message);
        response.put("timestamp", LocalDateTime.now());
        response.put("status", status.value());
        if (additionalFields != null) {
            response.putAll(additionalFields);
        }
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        logger.warn("User already exists: {}", ex.getMessage());
        return buildErrorResponse(ex.getMessage(), HttpStatus.CONFLICT, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        logger.warn("Validation error: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            logger.debug("Field validation failed: {} -> {}", error.getField(), error.getDefaultMessage());
            errors.put(error.getField(), error.getDefaultMessage());
        });

        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        logger.warn("Authentication failed: {}", ex.getMessage());

        Map<String, String> error = new HashMap<>();
        error.put("error", "Invalid username or password");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMalformedJson(HttpMessageNotReadableException ex) {
        logger.warn("Malformed JSON request: {}", ex.getMostSpecificCause().getMessage());

        Map<String, Object> additionalFields = new HashMap<>();
        additionalFields.put("details", ex.getMostSpecificCause().getMessage());

        return buildErrorResponse("Malformed JSON request", HttpStatus.BAD_REQUEST, additionalFields);
    }

    @ExceptionHandler(AlertNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAlertNotFound(AlertNotFoundException ex) {
        logger.warn("Alert not found: {}", ex.getMessage());
        return buildErrorResponse(ex.getMessage(), HttpStatus.NOT_FOUND, null);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex) {
        logger.warn("User not found: {}", ex.getMessage());
        return buildErrorResponse(ex.getMessage(), HttpStatus.NOT_FOUND, null);
    }

    @ExceptionHandler(ConcurrentConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConcurrentConflict(ConcurrentConflictException ex) {
        logger.warn("Concurrent conflict: {}", ex.getMessage());
        return buildErrorResponse(ex.getMessage(), HttpStatus.CONFLICT, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAllExceptions(Exception ex) {
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }

        if (root instanceof AlertNotFoundException) {
            return handleAlertNotFound((AlertNotFoundException) root);
        } else if (root instanceof UserNotFoundException) {
            return handleUserNotFound((UserNotFoundException) root);
        } else if (root instanceof ConcurrentConflictException) {
            return handleConcurrentConflict((ConcurrentConflictException) root);
        }

        logger.error("Unhandled exception occurred", ex);

        Map<String, Object> additionalFields = new HashMap<>();
        additionalFields.put("message", ex.getMessage());
        additionalFields.put("timestamp", Instant.now().toString()); // keep Instant format here for variety if desired

        return buildErrorResponse("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR, additionalFields);
    }

}

package com.comforthub.backoffice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Translates cross-cutting application exceptions into HTTP responses for all
 * REST controllers. Per-controller {@code @ExceptionHandler} methods still take
 * precedence for the exceptions they declare.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** OWNER-only action attempted by a non-owner (or unresolved role). */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Forbidden", "message", ex.getMessage()));
    }
}

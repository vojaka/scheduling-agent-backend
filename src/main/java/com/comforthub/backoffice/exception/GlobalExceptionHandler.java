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

    /**
     * Bad input surfaced by service/mapper layers (e.g. an unparseable timestamp
     * or an unknown status) maps to 400, not a misleading 500, for controllers
     * that do not declare their own {@code IllegalArgumentException} handler.
     * Controllers with a local handler (e.g. {@code ShiftController}) still take
     * precedence.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Bad Request", "message", String.valueOf(ex.getMessage())));
    }
}

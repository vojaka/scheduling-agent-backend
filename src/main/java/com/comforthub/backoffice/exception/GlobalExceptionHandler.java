package com.comforthub.backoffice.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** OWNER-only action attempted by a non-owner (or unresolved role). */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Forbidden", "message", ex.getMessage()));
    }

    /**
     * Bad input surfaced by service/mapper layers (e.g. an unparseable timestamp
     * or an unknown status). Maps to 400 instead of a misleading 500 for the
     * controllers that do not declare their own {@code IllegalArgumentException}
     * handler.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Bad Request", "message", String.valueOf(ex.getMessage())));
    }

    /**
     * An upstream Bubble Data API call failed. {@code BubbleClient} wraps Bubble
     * transport/HTTP errors in a {@link RuntimeException} and re-throws; on the
     * pure Bubble-proxy controllers (stores, orders, categories, ...) that would
     * otherwise surface as a generic 500. Classify it as 502 Bad Gateway so the
     * failure is attributed to the upstream, not the backoffice. Application-level
     * exceptions with their own handlers (Forbidden, IllegalArgument) are matched
     * first and are unaffected.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleUpstream(RuntimeException ex) {
        log.error("Unhandled runtime exception (mapped to 502 Bad Gateway): {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "Bad Gateway", "message", String.valueOf(ex.getMessage())));
    }
}

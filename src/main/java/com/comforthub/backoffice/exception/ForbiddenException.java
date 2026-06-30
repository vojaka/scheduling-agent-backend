package com.comforthub.backoffice.exception;

/**
 * Thrown when an authenticated caller lacks the role required for an action —
 * e.g. a WORKER attempting an OWNER-only operation such as generating or
 * committing a schedule, or managing workers. Mapped to HTTP 403 by
 * {@link GlobalExceptionHandler}.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}

package com.comforthub.backoffice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class GeminiUnavailableException extends RuntimeException {
    public GeminiUnavailableException(String message) {
        super(message);
    }

    public GeminiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

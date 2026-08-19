package com.vplmqa.common;

import org.springframework.http.HttpStatus;

/**
 * Base runtime exception used to convey HTTP status and message.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    /**
     * Creates a new API exception.
     *
     * @param status the HTTP status to return
     * @param message the message to return
     */
    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * Returns the HTTP status associated with this exception.
     *
     * @return the status
     */
    public HttpStatus getStatus() {
        return status;
    }
}

package com.vplmqa.common;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested entity is not found.
 */
public class EntityNotFoundException extends ApiException {

    /**
     * Creates a new not-found exception with a message.
     *
     * @param message the message to return
     */
    public EntityNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}

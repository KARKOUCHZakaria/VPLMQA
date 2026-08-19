package com.figma.design.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, Long resourceId) {
        super(resourceName + " not found with id " + resourceId);
    }
}
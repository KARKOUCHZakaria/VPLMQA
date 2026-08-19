package com.vplmqa.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.UUID;

/**
 * Validator that checks whether a string is a UUID.
 */
public class UuidValidator implements ConstraintValidator<ValidUuid, String> {

    /**
     * Validates the UUID string.
     *
     * @param value the value to validate
     * @param context the validation context
     * @return true if valid or null, false otherwise
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}

package com.vplmqa.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload to initiate password reset.
 *
 * @param email user email address
 */
public record ResetPasswordRequest(
        @Email @NotBlank String email
) {
}

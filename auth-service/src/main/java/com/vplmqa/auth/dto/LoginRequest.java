package com.vplmqa.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for login.
 *
 * @param email user email address
 * @param password plaintext password
 */
public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password
) {
}

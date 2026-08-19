package com.vplmqa.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for user registration.
 *
 * @param email user email address
 * @param password plaintext password
 * @param fullName user's full name
 */
public record RegisterRequest(
        @Email @NotBlank String email,
        @Size(min = 8, max = 128) String password,
        @NotBlank String fullName
) {
}

package com.vplmqa.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload to confirm password reset.
 *
 * @param token reset token
 * @param newPassword new password
 */
public record ResetPasswordConfirmRequest(
        @NotBlank String token,
        @Size(min = 8, max = 128) String newPassword
) {
}

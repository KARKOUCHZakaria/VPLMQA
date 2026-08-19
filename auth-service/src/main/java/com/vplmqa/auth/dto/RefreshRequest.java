package com.vplmqa.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for token refresh or logout.
 *
 * @param refreshToken refresh token
 */
public record RefreshRequest(
        @NotBlank String refreshToken
) {
}

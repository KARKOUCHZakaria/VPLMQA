package com.vplmqa.auth.dto;

/**
 * Response payload containing access and refresh tokens.
 *
 * @param accessToken access token
 * @param refreshToken refresh token
 * @param tokenType token type (e.g., Bearer)
 * @param expiresIn access token expiration in seconds
 * @param requiresPasswordChange whether the user must change password
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        boolean requiresPasswordChange
) {
}

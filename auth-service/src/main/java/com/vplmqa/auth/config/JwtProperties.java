package com.vplmqa.auth.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT configuration properties for token generation and validation.
 *
 * @param secret the HMAC secret used to sign tokens
 * @param accessTokenTtlSeconds access token TTL in seconds
 * @param refreshTokenTtlSeconds refresh token TTL in seconds
 * @param issuer token issuer value
 */
@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @Min(60) long accessTokenTtlSeconds,
        @Min(300) long refreshTokenTtlSeconds,
        @NotBlank String issuer
) {
}

package com.vplmqa.gateway.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for Redis-backed rate limiting.
 *
 * @param replenishRate tokens added per second
 * @param burstCapacity maximum burst capacity
 * @param requestedTokens tokens requested per call
 */
@Validated
@ConfigurationProperties(prefix = "gateway.rate-limit")
public record RateLimitProperties(
        @Min(1) int replenishRate,
        @Min(1) int burstCapacity,
        @Min(1) int requestedTokens
) {
}

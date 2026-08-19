package com.vplmqa.gateway.security;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Checks JWT blacklist entries stored in Redis.
 */
@Service
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "jwt:blacklist:";

    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * Creates a new blacklist service.
     *
     * @param redisTemplate reactive Redis template
     */
    public TokenBlacklistService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Determines if a token is blacklisted.
     *
     * @param token the JWT token
     * @return true if blacklisted, false otherwise
     */
    public Mono<Boolean> isBlacklisted(String token) {
        return redisTemplate.hasKey(KEY_PREFIX + token).map(Boolean::booleanValue);
    }
}

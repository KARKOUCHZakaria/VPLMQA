package com.vplmqa.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Manages blacklisted JWTs in Redis.
 */
@Service
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    /**
     * Creates the blacklist service.
     *
     * @param redisTemplate Redis template
     */
    public TokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Blacklists a token for the provided TTL.
     *
     * @param token token to blacklist
     * @param ttl time to live
     */
    public void blacklist(String token, Duration ttl) {
        redisTemplate.opsForValue().set(KEY_PREFIX + token, "1", ttl);
    }

    /**
     * Checks whether a token is blacklisted.
     *
     * @param token token to check
     * @return true if blacklisted
     */
    public boolean isBlacklisted(String token) {
        Boolean exists = redisTemplate.hasKey(KEY_PREFIX + token);
        return exists != null && exists;
    }
}

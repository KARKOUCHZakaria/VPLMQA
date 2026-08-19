package com.vplmqa.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Service for issuing and validating password reset tokens.
 */
@Service
public class PasswordResetService {

    private static final String KEY_PREFIX = "password:reset:";
    private static final Duration TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    /**
     * Creates the password reset service.
     *
     * @param redisTemplate Redis template
     */
    public PasswordResetService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Creates a reset token for the given email.
     *
     * @param email user email
     * @return reset token
     */
    public String createToken(String email) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + token, email, TTL);
        return token;
    }

    /**
     * Resolves the email for a reset token.
     *
     * @param token reset token
     * @return email or null if missing
     */
    public String resolveEmail(String token) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + token);
    }

    /**
     * Deletes a reset token after use.
     *
     * @param token reset token
     */
    public void deleteToken(String token) {
        redisTemplate.delete(KEY_PREFIX + token);
    }
}

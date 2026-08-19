package com.vplmqa.auth.service;

import com.vplmqa.auth.config.JwtProperties;
import com.vplmqa.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Service for generating and validating JWTs.
 */
@Service
public class JwtService {

    private final JwtProperties properties;

    /**
     * Creates the JWT service.
     *
     * @param properties JWT properties
     */
    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    /**
     * Generates an access token for a user.
     *
     * @param user the user
     * @return access token
     */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.accessTokenTtlSeconds())))
                .claim("role", user.getRole().getName().name())
                .signWith(Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    /**
     * Generates a refresh token for a user.
     *
     * @param user the user
     * @return refresh token
     */
    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.refreshTokenTtlSeconds())))
                .claim("role", user.getRole().getName().name())
                .signWith(Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    /**
     * Validates a token signature and expiration.
     *
     * @param token the token
     * @return true if valid
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Extracts the user ID (subject) from the token.
     *
     * @param token the token
     * @return user ID
     */
    public String extractUserId(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extracts the role claim from the token.
     *
     * @param token the token
     * @return role name
     */
    public String extractUserRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /**
     * Returns access token TTL in seconds.
     *
     * @return access token TTL
     */
    public long getAccessTokenTtlSeconds() {
        return properties.accessTokenTtlSeconds();
    }

    /**
     * Returns refresh token TTL in seconds.
     *
     * @return refresh token TTL
     */
    public long getRefreshTokenTtlSeconds() {
        return properties.refreshTokenTtlSeconds();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

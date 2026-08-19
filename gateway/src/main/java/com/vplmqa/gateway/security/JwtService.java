package com.vplmqa.gateway.security;

import com.vplmqa.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Validates and parses JWTs in the gateway.
 */
@Service
public class JwtService {

    private final JwtProperties properties;

    /**
     * Creates a new JWT service.
     *
     * @param properties JWT properties
     */
    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    /**
     * Validates the JWT signature and expiration.
     *
     * @param token the JWT token
     * @return true if valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(JwtService.class).error("Token validation error: ", ex);
            return false;
        }
    }

    /**
     * Extracts the user ID (subject) from the token.
     *
     * @param token the JWT token
     * @return the user ID
     */
    public String extractUserId(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extracts the user role claim from the token.
     *
     * @param token the JWT token
     * @return the role name
     */
    public String extractUserRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

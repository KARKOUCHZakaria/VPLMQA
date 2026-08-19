package com.vplmqa.auth.repository;

import com.vplmqa.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for refresh tokens.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Finds a non-revoked token by value.
     *
     * @param token the token value
     * @return the refresh token
     */
    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);
}

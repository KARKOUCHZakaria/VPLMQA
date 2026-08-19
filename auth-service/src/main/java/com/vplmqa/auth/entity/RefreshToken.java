package com.vplmqa.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token entity for token rotation.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    /** Primary key identifier. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Refresh token string. */
    @Column(nullable = false, unique = true)
    private String token;

    /** User relationship. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Expiration timestamp. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Revoked flag. */
    @Column(nullable = false)
    private boolean revoked;

    /** Creation timestamp. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Default constructor. */
    public RefreshToken() {
    }

    /**
     * Returns the token ID.
     *
     * @return token ID
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets the token ID.
     *
     * @param id token ID
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Returns the token string.
     *
     * @return the token
     */
    public String getToken() {
        return token;
    }

    /**
     * Sets the token string.
     *
     * @param token the token
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * Returns the user.
     *
     * @return the user
     */
    public User getUser() {
        return user;
    }

    /**
     * Sets the user.
     *
     * @param user the user
     */
    public void setUser(User user) {
        this.user = user;
    }

    /**
     * Returns the expiration timestamp.
     *
     * @return the expiration time
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Sets the expiration timestamp.
     *
     * @param expiresAt the expiration time
     */
    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * Returns whether the token is revoked.
     *
     * @return true if revoked
     */
    public boolean isRevoked() {
        return revoked;
    }

    /**
     * Sets whether the token is revoked.
     *
     * @param revoked revoked flag
     */
    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    /**
     * Returns the creation timestamp.
     *
     * @return creation time
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation timestamp.
     *
     * @param createdAt creation time
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

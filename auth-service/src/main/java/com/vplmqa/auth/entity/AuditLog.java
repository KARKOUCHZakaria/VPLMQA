package com.vplmqa.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit log entity for security events.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    /** Primary key identifier. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** User identifier associated with the action. */
    @Column(name = "user_id")
    private UUID userId;

    /** Action name. */
    @Column(nullable = false)
    private String action;

    /** Client IP address. */
    @Column(name = "ip_address")
    private String ipAddress;

    /** User agent string. */
    @Column(name = "user_agent")
    private String userAgent;

    /** Success flag. */
    @Column(nullable = false)
    private boolean success;

    /** Creation timestamp. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Default constructor. */
    public AuditLog() {
    }

    /**
     * Returns the log ID.
     *
     * @return log ID
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets the log ID.
     *
     * @param id log ID
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Returns the user ID.
     *
     * @return user ID
     */
    public UUID getUserId() {
        return userId;
    }

    /**
     * Sets the user ID.
     *
     * @param userId user ID
     */
    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    /**
     * Returns the action.
     *
     * @return action name
     */
    public String getAction() {
        return action;
    }

    /**
     * Sets the action.
     *
     * @param action action name
     */
    public void setAction(String action) {
        this.action = action;
    }

    /**
     * Returns the IP address.
     *
     * @return IP address
     */
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * Sets the IP address.
     *
     * @param ipAddress IP address
     */
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * Returns the user agent.
     *
     * @return user agent
     */
    public String getUserAgent() {
        return userAgent;
    }

    /**
     * Sets the user agent.
     *
     * @param userAgent user agent
     */
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    /**
     * Returns whether the action was successful.
     *
     * @return true if successful
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Sets whether the action was successful.
     *
     * @param success success flag
     */
    public void setSuccess(boolean success) {
        this.success = success;
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

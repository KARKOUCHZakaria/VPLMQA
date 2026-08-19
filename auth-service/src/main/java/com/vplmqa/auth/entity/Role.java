package com.vplmqa.auth.entity;

import com.vplmqa.auth.enumtype.RoleEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Role entity representing a user role and permissions.
 */
@Entity
@Table(name = "roles")
public class Role {

    /** Primary key identifier. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Role name enumeration. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private RoleEnum name;

    /** Permissions JSON for the role. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> permissions;

    /** Creation timestamp. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Default constructor. */
    public Role() {
    }

    /**
     * Returns the role ID.
     *
     * @return the role ID
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets the role ID.
     *
     * @param id the role ID
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Returns the role name.
     *
     * @return the role name
     */
    public RoleEnum getName() {
        return name;
    }

    /**
     * Sets the role name.
     *
     * @param name the role name
     */
    public void setName(RoleEnum name) {
        this.name = name;
    }

    /**
     * Returns the permissions JSON.
     *
     * @return permissions map
     */
    public Map<String, Object> getPermissions() {
        return permissions;
    }

    /**
     * Sets the permissions JSON.
     *
     * @param permissions permissions map
     */
    public void setPermissions(Map<String, Object> permissions) {
        this.permissions = permissions;
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

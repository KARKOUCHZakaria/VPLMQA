package com.vplmqa.auth.repository;

import com.vplmqa.auth.entity.Role;
import com.vplmqa.auth.enumtype.RoleEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for role entities.
 */
public interface RoleRepository extends JpaRepository<Role, UUID> {

    /**
     * Finds a role by name.
     *
     * @param name the role name
     * @return role if found
     */
    Optional<Role> findByName(RoleEnum name);
}

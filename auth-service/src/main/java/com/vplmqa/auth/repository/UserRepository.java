package com.vplmqa.auth.repository;

import com.vplmqa.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for user entities.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds a user by email.
     *
     * @param email the email
     * @return the user if found
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks if a user exists by email.
     *
     * @param email the email
     * @return true if exists
     */
    boolean existsByEmail(String email);
}

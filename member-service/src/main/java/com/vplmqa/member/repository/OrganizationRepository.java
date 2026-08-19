package com.vplmqa.member.repository;

import com.vplmqa.member.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for organizations.
 */
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    /**
     * Finds an organization by slug.
     *
     * @param slug slug value
     * @return organization if present
     */
    Optional<Organization> findBySlug(String slug);
}

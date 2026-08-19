package com.vplmqa.ticket.repository;

import com.vplmqa.ticket.entity.AzureDevOpsConnection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AzureDevOpsConnectionRepository extends JpaRepository<AzureDevOpsConnection, UUID> {
}

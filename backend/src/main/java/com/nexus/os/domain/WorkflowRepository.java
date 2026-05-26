package com.nexus.os.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {
    List<Workflow> findByTenantIdAndActive(UUID tenantId, boolean active);
    Optional<Workflow> findByTenantIdAndSlugAndVersion(UUID tenantId, String slug, int version);
}

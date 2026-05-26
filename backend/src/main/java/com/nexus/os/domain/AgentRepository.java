package com.nexus.os.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentRepository extends JpaRepository<AgentEntity, UUID> {
    List<AgentEntity> findByTenantIdAndActive(UUID tenantId, boolean active);
    Optional<AgentEntity> findByTenantIdAndSlug(UUID tenantId, String slug);
}

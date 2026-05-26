package com.nexus.os.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, UUID> {
    Page<WorkflowRun> findByTenantIdOrderByStartedAtDesc(UUID tenantId, Pageable pageable);
    Page<WorkflowRun> findByTenantIdAndStatusOrderByStartedAtDesc(UUID tenantId, WorkflowRun.Status status, Pageable pageable);
}

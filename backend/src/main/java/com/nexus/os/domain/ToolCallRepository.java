package com.nexus.os.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ToolCallRepository extends JpaRepository<ToolCallEntity, UUID> {
    List<ToolCallEntity> findByWorkflowRunIdOrderByOccurredAtAsc(UUID workflowRunId);
}

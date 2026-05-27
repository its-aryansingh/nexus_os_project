package com.nexus.os.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MemoryChunkRepository extends JpaRepository<MemoryChunk, UUID> {
    Page<MemoryChunk> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}

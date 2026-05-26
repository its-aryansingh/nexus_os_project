package com.nexus.os.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Polls undispatched rows for the {@code OutboxDispatcher}. Matches the
     * partial index defined in V003 (WHERE dispatched_at IS NULL).
     */
    @Query("SELECT o FROM OutboxEvent o WHERE o.dispatchedAt IS NULL ORDER BY o.createdAt ASC")
    List<OutboxEvent> findUndispatched(Pageable pageable);
}

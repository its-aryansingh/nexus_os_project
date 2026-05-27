package com.nexus.os.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findBySessionIdOrderByOccurredAtAsc(UUID sessionId);
    long countBySessionId(UUID sessionId);
}

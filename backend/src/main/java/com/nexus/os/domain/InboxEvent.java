package com.nexus.os.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Consumer idempotency row. Inserted before each Kafka/webhook event is
 * processed; the PRIMARY KEY constraint causes duplicate deliveries to throw
 * a unique-violation, which the consumer catches + drops silently.
 * See docs/SYSTEM_DESIGN.md §3.6.
 */
@Entity
@Table(name = "inbox")
public class InboxEvent {

    @Id
    @Column(name = "event_id", length = 255)
    private String eventId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 128)
    private String consumer;

    @Column(name = "source_topic", length = 128)
    private String sourceTopic;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        if (this.processedAt == null) {
            this.processedAt = OffsetDateTime.now();
        }
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getConsumer() { return consumer; }
    public void setConsumer(String consumer) { this.consumer = consumer; }
    public String getSourceTopic() { return sourceTopic; }
    public void setSourceTopic(String sourceTopic) { this.sourceTopic = sourceTopic; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(OffsetDateTime processedAt) { this.processedAt = processedAt; }
}

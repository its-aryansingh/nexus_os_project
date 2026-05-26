package com.nexus.os.kafka;

import com.nexus.os.domain.InboxEvent;
import com.nexus.os.domain.InboxRepository;
import com.nexus.os.temporal.WorkflowStarter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Consumes {@code nexus.inbound.messages} — the channel-agnostic inbound
 * message bus. Webhooks (WhatsApp, Slack, etc.) and API ingress write to
 * this topic; this consumer is the single seam that kicks off Temporal
 * workflows.
 *
 * <p>Idempotency: every event writes to the {@code inbox} table BEFORE
 * processing. {@code event_id PRIMARY KEY} causes Kafka duplicates to
 * fail-fast on the insert, which we catch + drop. See SYSTEM_DESIGN.md §3.6.
 */
@Component
public class InboundMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(InboundMessageConsumer.class);
    private static final String CONSUMER_NAME = "InboundMessageConsumer";

    private final InboxRepository inbox;
    private final WorkflowStarter starter;
    private final MeterRegistry meters;

    public InboundMessageConsumer(InboxRepository inbox, WorkflowStarter starter, MeterRegistry meters) {
        this.inbox = inbox;
        this.starter = starter;
        this.meters = meters;
    }

    @KafkaListener(topics = "nexus.inbound.messages", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onMessage(
            @Payload Map<String, Object> payload,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(name = KafkaHeaders.OFFSET) long offset,
            @Header(name = KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(name = "x-event-id", required = false) String eventIdHeader,
            Acknowledgment ack
    ) {
        final var eventId = eventIdHeader != null ? eventIdHeader : "kafka:" + partition + ":" + offset;

        // Tenant id from key (producers set key = tenant id)
        final UUID tenantId;
        try {
            tenantId = UUID.fromString(key);
        } catch (IllegalArgumentException malformed) {
            log.warn("Dropping inbound message with non-uuid key: {}", key);
            ack.acknowledge();
            meters.counter("nexus.kafka.dropped_malformed_key").increment();
            return;
        }

        // Inbox dedupe — duplicate Kafka deliveries fail-fast on PK constraint.
        try {
            final var row = new InboxEvent();
            row.setEventId(eventId);
            row.setTenantId(tenantId);
            row.setConsumer(CONSUMER_NAME);
            row.setSourceTopic("nexus.inbound.messages");
            inbox.save(row);
        } catch (DataIntegrityViolationException duplicate) {
            log.debug("Duplicate inbound message {}, skipping", eventId);
            ack.acknowledge();
            meters.counter("nexus.kafka.duplicate_events").increment();
            return;
        }

        try {
            // v0.1 routes every inbound message through the default workflow.
            // v0.2 looks up channel_subscriptions to pick a tenant-specific
            // workflow per channel + address.
            final var workflowId = starter.startAgentOrchestration(tenantId, null, payload);
            log.info("Dispatched inbound message to workflow {} (tenant {})", workflowId, tenantId);
            ack.acknowledge();
            meters.counter("nexus.kafka.processed", "topic", "nexus.inbound.messages").increment();
        } catch (Exception fail) {
            // Do NOT ack on failure — Kafka redelivers. The inbox row is
            // committed (idempotency holds), so the retry deduplicates
            // unless this transaction rolls back, in which case the row is
            // gone and the retry processes cleanly. Both paths are safe.
            log.error("Failed to dispatch inbound message {}: {}", eventId, fail.getMessage(), fail);
            meters.counter("nexus.kafka.failed", "topic", "nexus.inbound.messages").increment();
            throw fail;
        }
    }
}

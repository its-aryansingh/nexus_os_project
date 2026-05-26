package com.nexus.os.kafka;

import com.nexus.os.domain.InboxEvent;
import com.nexus.os.domain.InboxRepository;
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
    private final MeterRegistry meters;

    public InboundMessageConsumer(InboxRepository inbox, MeterRegistry meters) {
        this.inbox = inbox;
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
            process(tenantId, payload);
            ack.acknowledge();
            meters.counter("nexus.kafka.processed", "topic", "nexus.inbound.messages").increment();
        } catch (Exception fail) {
            // Do NOT ack on failure — Kafka will redeliver. The inbox row is
            // still committed (we want to remember we tried), so the redelivery
            // will also be deduped — DLQ flow lives in v0.2.
            log.error("Failed to process inbound message {}: {}", eventId, fail.getMessage(), fail);
            meters.counter("nexus.kafka.failed", "topic", "nexus.inbound.messages").increment();
            throw fail;
        }
    }

    /**
     * Real implementation kicks off the AgentOrchestrationWorkflow. v0.1
     * scaffolding logs only — workflow start lives in v0.2 once the
     * WorkflowClient bean is exposed to non-Temporal beans.
     */
    private void process(UUID tenantId, Map<String, Object> payload) {
        log.info("Inbound message for tenant {}: keys={}", tenantId, payload.keySet());
        // TODO v0.2: workflowClient.start(AgentOrchestrationWorkflow::orchestrate, JSON.stringify(payload));
    }
}

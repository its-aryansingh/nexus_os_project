package com.nexus.os.kafka;

import com.nexus.os.domain.InboxEvent;
import com.nexus.os.domain.InboxRepository;
import com.nexus.os.domain.WorkflowRun;
import com.nexus.os.domain.WorkflowRunRepository;
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

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CQRS read-model projector for {@code nexus.agent.events}. Picks up the
 * {@code workflow.run.completed} / {@code workflow.run.failed} events
 * emitted by {@link com.nexus.os.temporal.activities.AgentActivityImpl}
 * and updates the corresponding {@link WorkflowRun} row.
 *
 * <p>Closes the loop documented in ARCHITECTURE.md §4.1 + SYSTEM_DESIGN.md
 * §2.3 (CQRS).
 *
 * <p>Idempotency: uses the {@code inbox} table — duplicate Kafka deliveries
 * fail-fast on the event-id PK constraint, which we trap + ack-and-drop.
 */
@Component
public class WorkflowEventProjector {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEventProjector.class);
    private static final String CONSUMER_NAME = "WorkflowEventProjector";

    private final WorkflowRunRepository runs;
    private final InboxRepository inbox;
    private final MeterRegistry meters;

    public WorkflowEventProjector(WorkflowRunRepository runs, InboxRepository inbox, MeterRegistry meters) {
        this.runs = runs;
        this.inbox = inbox;
        this.meters = meters;
    }

    @KafkaListener(topics = "nexus.agent.events", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void onEvent(
            @Payload Map<String, Object> payload,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(name = KafkaHeaders.OFFSET) long offset,
            @Header(name = KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(name = "x-event-id", required = false) String eventIdHeader,
            Acknowledgment ack
    ) {
        final var eventType = String.valueOf(payload.getOrDefault("event_type", ""));
        if (!isWorkflowRunUpdate(eventType)) {
            // We share the topic with other downstream projectors; ignore
            // events that aren't ours.
            ack.acknowledge();
            return;
        }

        final var eventId = eventIdHeader != null ? eventIdHeader : "kafka:" + partition + ":" + offset;
        final UUID tenantId;
        try {
            tenantId = UUID.fromString(String.valueOf(payload.get("tenant_id")));
        } catch (IllegalArgumentException malformed) {
            log.warn("Dropping workflow event with bad tenant_id: {}", payload.get("tenant_id"));
            ack.acknowledge();
            return;
        }

        // Inbox dedupe — duplicate delivery throws on PK conflict.
        try {
            final var row = new InboxEvent();
            row.setEventId(eventId);
            row.setTenantId(tenantId);
            row.setConsumer(CONSUMER_NAME);
            row.setSourceTopic("nexus.agent.events");
            inbox.save(row);
        } catch (DataIntegrityViolationException duplicate) {
            log.debug("Duplicate workflow event {} skipped", eventId);
            ack.acknowledge();
            meters.counter("nexus.kafka.duplicate_events", "consumer", CONSUMER_NAME).increment();
            return;
        }

        try {
            final UUID runId = UUID.fromString(String.valueOf(payload.get("workflow_run_id")));
            final var run = runs.findById(runId).orElse(null);
            if (run == null) {
                log.warn("Workflow event for unknown run {} — projection skipped", runId);
                ack.acknowledge();
                return;
            }
            project(run, eventType, payload);
            runs.save(run);
            ack.acknowledge();
            meters.counter("nexus.workflow.projected", "type", shortType(eventType)).increment();
        } catch (Exception fail) {
            // Do NOT ack — Kafka retries; inbox row stays committed, so the
            // retry trips the dedupe and exits cleanly. The read-model row
            // therefore eventually converges or stays stale forever — pick.
            log.error("Failed to project workflow event {}: {}", eventId, fail.getMessage(), fail);
            meters.counter("nexus.kafka.failed", "consumer", CONSUMER_NAME).increment();
            throw fail;
        }
    }

    private void project(WorkflowRun run, String eventType, Map<String, Object> payload) {
        if ("workflow.run.completed".equals(eventType)) {
            run.setStatus(WorkflowRun.Status.completed);
            final var output = payload.get("output");
            if (output != null) {
                run.setOutputPayload(Map.of("text", output.toString()));
            }
        } else if ("workflow.run.failed".equals(eventType)) {
            run.setStatus(WorkflowRun.Status.failed);
            final var error = payload.get("error");
            if (error != null) run.setError(error.toString());
        }
        final var ms = payload.get("duration_ms");
        if (ms instanceof Number n) run.setDurationMs(n.intValue());
        run.setFinishedAt(OffsetDateTime.now());
    }

    private static boolean isWorkflowRunUpdate(String eventType) {
        return "workflow.run.completed".equals(eventType) || "workflow.run.failed".equals(eventType);
    }

    private static String shortType(String t) {
        // metric tag — keep cardinality bounded
        return switch (t) {
            case "workflow.run.completed" -> "completed";
            case "workflow.run.failed"    -> "failed";
            default -> "other";
        };
    }
}

package com.nexus.os.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.os.domain.OutboxEvent;
import com.nexus.os.domain.OutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Polls the {@code outbox} table for undispatched events and publishes them
 * to Kafka. See docs/SYSTEM_DESIGN.md §3.5 (Outbox Pattern).
 *
 * <p>Runs every 200ms by default; the partial index
 * {@code idx_outbox_undispatched WHERE dispatched_at IS NULL} keeps the
 * scan O(undispatched) regardless of total outbox size.
 *
 * <p>Idempotency: if Kafka publish succeeds but the {@code UPDATE
 * outbox SET dispatched_at} fails (rare — same DB, same tx), the next
 * tick republishes — consumers must be idempotent (see
 * {@link InboundMessageConsumer}). Trade-off accepted: at-least-once
 * publish is strictly better than dual-write inconsistency.
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;
    private final Timer dispatchTimer;

    public OutboxDispatcher(
            OutboxRepository outbox,
            KafkaTemplate<String, Object> kafka,
            ObjectMapper objectMapper,
            MeterRegistry meters
    ) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.meters = meters;
        this.dispatchTimer = Timer.builder("nexus.outbox.dispatch")
                .description("Time to publish one outbox event to Kafka")
                .publishPercentileHistogram()
                .register(meters);
    }

    @Scheduled(fixedDelayString = "${nexus.outbox.poll-interval-ms:200}")
    @Transactional
    public void poll() {
        final var batch = outbox.findUndispatched(PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) {
            return;
        }
        for (final var ev : batch) {
            dispatchOne(ev);
        }
        meters.gauge("nexus.outbox.batch_size", batch.size());
    }

    private void dispatchOne(OutboxEvent ev) {
        final var start = System.nanoTime();
        try {
            // partition key defaults to the tenant id if not set explicitly
            final var key = ev.getPartitionKey() != null ? ev.getPartitionKey() : ev.getTenantId().toString();
            final SendResult<String, Object> result =
                    kafka.send(ev.getTopic(), key, ev.getPayload()).get(10, java.util.concurrent.TimeUnit.SECONDS);

            ev.setDispatchedAt(OffsetDateTime.now());
            ev.setDispatchAttempts(ev.getDispatchAttempts() + 1);
            outbox.save(ev);

            meters.counter("nexus.outbox.dispatched", "topic", ev.getTopic()).increment();
            final var lagSeconds = Duration.between(ev.getCreatedAt(), ev.getDispatchedAt()).toMillis() / 1000.0;
            meters.gauge("nexus.outbox.lag_seconds", lagSeconds);

            if (log.isDebugEnabled()) {
                log.debug("Dispatched outbox event {} to topic {} (offset {}, partition {})",
                        ev.getId(), ev.getTopic(), result.getRecordMetadata().offset(),
                        result.getRecordMetadata().partition());
            }
        } catch (Exception fail) {
            ev.setDispatchAttempts(ev.getDispatchAttempts() + 1);
            ev.setLastDispatchError(fail.getClass().getSimpleName() + ": " + fail.getMessage());
            outbox.save(ev);
            meters.counter("nexus.outbox.failures", "topic", ev.getTopic()).increment();
            log.warn("Outbox dispatch failed for event {} (attempt {}): {}",
                    ev.getId(), ev.getDispatchAttempts(), fail.getMessage());
        } finally {
            dispatchTimer.record(Duration.ofNanos(System.nanoTime() - start));
        }
    }
}

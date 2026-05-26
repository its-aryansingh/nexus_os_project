package com.nexus.os.agents.rag;

import com.nexus.os.tenancy.TenantContext;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tenant-partitioned Qdrant store wrapper. Each tenant gets its own
 * collection ({@code tenant_<uuid>}) so cross-tenant recall is impossible
 * at the storage layer — the strongest defense-in-depth alongside the
 * Postgres RLS guarantees.
 *
 * <p>v0.1 ships an in-memory cosine-search shim instead of the real
 * Qdrant gRPC client. Same interface, so swapping in
 * {@code io.qdrant:client} in v0.2 is contained to this file.
 *
 * <p>Wrapped with the {@code qdrant} Resilience4j circuit breaker so a
 * vector store outage degrades agents to no-RAG mode rather than
 * cascading failures up to the user.
 */
@Service
public class QdrantStore {

    private static final Logger log = LoggerFactory.getLogger(QdrantStore.class);

    /** collectionName -> (pointId -> StoredPoint). */
    private final Map<String, Map<String, StoredPoint>> collections = new ConcurrentHashMap<>();

    private final String collectionPrefix;
    private final MeterRegistry meters;

    public QdrantStore(
            @Value("${qdrant.default-collection-prefix:tenant_}") String collectionPrefix,
            MeterRegistry meters
    ) {
        this.collectionPrefix = collectionPrefix;
        this.meters = meters;
    }

    /** Resolve the collection name for the current tenant binding. */
    public String collectionForCurrentTenant() {
        return collectionPrefix + TenantContext.require();
    }

    @CircuitBreaker(name = "qdrant", fallbackMethod = "upsertFallback")
    public String upsert(String pointId, float[] vector, Map<String, Object> payload) {
        final var name = collectionForCurrentTenant();
        collections.computeIfAbsent(name, k -> new ConcurrentHashMap<>())
                .put(pointId, new StoredPoint(pointId, vector, payload));
        meters.counter("nexus.qdrant.upserts").increment();
        if (log.isDebugEnabled()) {
            log.debug("[qdrant-mem] upsert {} into {} (dim={})", pointId, name, vector.length);
        }
        return pointId;
    }

    @CircuitBreaker(name = "qdrant", fallbackMethod = "searchFallback")
    public List<ScoredPoint> search(float[] query, int topK) {
        final var name = collectionForCurrentTenant();
        final var coll = collections.getOrDefault(name, Map.of());
        if (coll.isEmpty()) return List.of();

        final var ranked = new ArrayList<ScoredPoint>(coll.size());
        for (final var entry : coll.entrySet()) {
            final var pt = entry.getValue();
            ranked.add(new ScoredPoint(pt.id(), EmbeddingService.cosine(query, pt.vector()), pt.payload()));
        }
        ranked.sort((a, b) -> Float.compare(b.score(), a.score()));
        meters.counter("nexus.qdrant.searches").increment();
        return ranked.subList(0, Math.min(topK, ranked.size()));
    }

    public int sizeForCurrentTenant() {
        return collections.getOrDefault(collectionForCurrentTenant(), Map.of()).size();
    }

    // ── Resilience4j fallbacks ──────────────────────────────────────────────

    @SuppressWarnings("unused")
    private String upsertFallback(String pointId, float[] vector, Map<String, Object> payload, Throwable t) {
        log.warn("Qdrant upsert circuit open ({}); discarding point {}", t.getMessage(), pointId);
        meters.counter("nexus.qdrant.fallback", "op", "upsert").increment();
        return pointId;   // no-op upsert is safe — caller already wrote the metadata row
    }

    @SuppressWarnings("unused")
    private List<ScoredPoint> searchFallback(float[] query, int topK, Throwable t) {
        log.warn("Qdrant search circuit open ({}); returning empty result", t.getMessage());
        meters.counter("nexus.qdrant.fallback", "op", "search").increment();
        return List.of();
    }

    /** One point as stored in the v0.1 in-memory shim. */
    public record StoredPoint(String id, float[] vector, Map<String, Object> payload) {}

    /** One similarity-ranked result. */
    public record ScoredPoint(String id, float score, Map<String, Object> payload) {}

    /** Test/debug helper — fresh point id. */
    public static String newPointId() {
        return UUID.randomUUID().toString();
    }
}

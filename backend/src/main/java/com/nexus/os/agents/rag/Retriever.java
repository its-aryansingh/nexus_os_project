package com.nexus.os.agents.rag;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the full RAG read path: query → embed → vector search →
 * (optional rerank) → context assembly. Consumed by the
 * {@code AgentCapability.DataRetrieval} branch in the agent activity.
 *
 * <p>See SYSTEM_DESIGN.md §4.1.
 */
@Service
public class Retriever {

    private static final Logger log = LoggerFactory.getLogger(Retriever.class);

    private final EmbeddingService embedding;
    private final QdrantStore store;
    private final Timer timer;

    public Retriever(EmbeddingService embedding, QdrantStore store, MeterRegistry meters) {
        this.embedding = embedding;
        this.store = store;
        this.timer = Timer.builder("nexus.rag.retrieve")
                .description("End-to-end RAG retrieval time")
                .publishPercentileHistogram()
                .register(meters);
    }

    /**
     * Retrieve the top-K most relevant chunks for {@code query} from the
     * caller's tenant collection. Returns ranked {@link Context} ready to
     * paste into an LLM prompt.
     */
    public List<Context> retrieve(String query, int topK) {
        final var start = System.nanoTime();
        try {
            if (query == null || query.isBlank()) return List.of();
            final var qVec = embedding.embed(query);
            final var hits = store.search(qVec, topK);
            return hits.stream()
                    .map(h -> new Context(
                            h.id(),
                            h.score(),
                            text(h.payload()),
                            h.payload() == null ? Map.of() : new LinkedHashMap<>(h.payload())))
                    .toList();
        } finally {
            timer.record(Duration.ofNanos(System.nanoTime() - start));
        }
    }

    /**
     * Convenience — render retrieved contexts into a prompt prelude that
     * the LLM can ground its answer on. Each chunk is annotated with a
     * citation marker [1], [2], ... so the {@code HallucinationGuard}
     * citation check can verify the model used them.
     */
    public String renderPrompt(List<Context> contexts, String userQuery) {
        if (contexts == null || contexts.isEmpty()) {
            return userQuery == null ? "" : userQuery;
        }
        final var sb = new StringBuilder("Use the following context to answer the question. ")
                .append("Cite sources by their [n] marker.\n\n");
        int n = 1;
        for (final var c : contexts) {
            sb.append("[").append(n++).append("] (score ").append(String.format("%.3f", c.score())).append(")\n");
            sb.append(c.text()).append("\n\n");
        }
        sb.append("Question: ").append(userQuery == null ? "" : userQuery);
        return sb.toString();
    }

    /**
     * Index a freshly-embedded chunk into the caller's tenant collection.
     * Returns the Qdrant point id (matches the {@code qdrant_point_id}
     * column on {@code memory_chunks}).
     */
    public String index(String text, Map<String, Object> payload) {
        final var vec = embedding.embed(text);
        final var pointId = QdrantStore.newPointId();
        final var enriched = new LinkedHashMap<>(payload == null ? Map.of() : payload);
        enriched.putIfAbsent("text", text);
        return store.upsert(pointId, vec, enriched);
    }

    private static String text(Map<String, Object> payload) {
        if (payload == null) return "";
        final var v = payload.get("text");
        return v == null ? "" : v.toString();
    }

    /** One retrieved + scored chunk. */
    public record Context(String id, float score, String text, Map<String, Object> payload) {}
}

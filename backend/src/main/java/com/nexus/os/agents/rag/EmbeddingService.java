package com.nexus.os.agents.rag;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Produces embedding vectors for text chunks. Caches per text hash in
 * Redis to avoid paying twice for identical text — typical hit rates
 * around 70-80% on real corpora.
 *
 * <p>v0.1 ships a deterministic mock embedder (hash → seed → uniform
 * vector) so the system runs offline. v0.2 swaps in LangChain4j's
 * OpenAiEmbeddingModel under the same interface.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final RedisTemplate<String, Object> redis;
    private final MeterRegistry meters;
    private final int vectorSize;
    private final String modelName;

    public EmbeddingService(
            RedisTemplate<String, Object> redis,
            MeterRegistry meters,
            @Value("${qdrant.vector-size:1536}") int vectorSize,
            @Value("${nexus.embedding.model:text-embedding-3-small}") String modelName
    ) {
        this.redis = redis;
        this.meters = meters;
        this.vectorSize = vectorSize;
        this.modelName = modelName;
    }

    /** Embed a single text. Returns a normalized float[] of length {@code vectorSize}. */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[vectorSize];
        }
        final var key = cacheKey(text);
        final var hit = redis.opsForValue().get(key);
        if (hit instanceof float[] cached) {
            meters.counter("nexus.embeddings.cache_hits").increment();
            return cached;
        }
        if (hit instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Number) {
            // Jackson deserializes float arrays as List<Number> — convert back.
            final var arr = new float[list.size()];
            for (int i = 0; i < list.size(); i++) arr[i] = ((Number) list.get(i)).floatValue();
            meters.counter("nexus.embeddings.cache_hits").increment();
            return arr;
        }
        meters.counter("nexus.embeddings.cache_misses").increment();

        final var vec = computeMockEmbedding(text);
        redis.opsForValue().set(key, vec, 24, TimeUnit.HOURS);
        return vec;
    }

    /** Batch convenience — embeds in input order; uses cache per item. */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null) return List.of();
        return texts.stream().map(this::embed).toList();
    }

    public int vectorSize() { return vectorSize; }
    public String modelName() { return modelName; }

    // ── internals ───────────────────────────────────────────────────────────

    private String cacheKey(String text) {
        try {
            final var md = MessageDigest.getInstance("SHA-256");
            md.update(modelName.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(text.getBytes(StandardCharsets.UTF_8));
            return "emb:" + HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException unreachable) {
            throw new IllegalStateException("SHA-256 unavailable", unreachable);
        }
    }

    /**
     * Deterministic mock embedding — same text always yields the same
     * vector, so the rest of the pipeline (Qdrant insert, retrieval)
     * exercises real code paths. Vectors are unit-normalized so cosine
     * similarity behaves sensibly.
     */
    private float[] computeMockEmbedding(String text) {
        if (log.isTraceEnabled()) {
            log.trace("[mock-embedder] generating {}-dim vector for text ({} chars)", vectorSize, text.length());
        }
        long seed = 1469598103934665603L;  // FNV-1a 64 offset
        for (final var ch : text.toCharArray()) {
            seed ^= ch;
            seed *= 1099511628211L;        // FNV-1a 64 prime
        }
        final RandomGenerator rng = RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
        final var vec = new float[vectorSize];
        double sumSq = 0;
        for (int i = 0; i < vectorSize; i++) {
            vec[i] = (float) (rng.nextDouble() * 2 - 1);
            sumSq += vec[i] * vec[i];
        }
        final var norm = (float) Math.sqrt(sumSq);
        if (norm > 0) {
            for (int i = 0; i < vectorSize; i++) vec[i] /= norm;
        }
        return vec;
    }

    /** Dot product / cosine similarity for two unit-normalized vectors. */
    public static float cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Dimension mismatch: " + a.length + " vs " + b.length);
        }
        float dot = 0;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        return dot;
    }

    /** Used by tests to verify cache shape. */
    static boolean isLikelyNormalized(float[] v) {
        float sumSq = 0;
        for (final var f : v) sumSq += f * f;
        return Math.abs(Math.sqrt(sumSq) - 1.0) < 1e-3;
    }

    @SuppressWarnings("unused")  // reserved for v0.2 batch inspect
    public List<float[]> mockOnlyFor(List<String> samples) {
        return samples.stream().map(this::computeMockEmbedding).toList();
    }

    @SuppressWarnings("unused")
    private static boolean equalsDeep(float[] a, float[] b) { return Arrays.equals(a, b); }
}

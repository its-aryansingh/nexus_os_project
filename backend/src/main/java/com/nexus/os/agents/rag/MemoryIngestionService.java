package com.nexus.os.agents.rag;

import com.nexus.os.domain.MemoryChunk;
import com.nexus.os.domain.MemoryChunkRepository;
import com.nexus.os.tenancy.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Write path for the RAG store. Composes {@link ChunkingStrategy} +
 * {@link EmbeddingService} + {@link QdrantStore} into one transactional
 * call so the relational {@code memory_chunks} rows and the Qdrant
 * points land together (or roll back together if anything throws).
 */
@Service
public class MemoryIngestionService {

    private static final Logger log = LoggerFactory.getLogger(MemoryIngestionService.class);
    private static final int DEFAULT_CHUNK_TOKENS = 500;
    private static final int DEFAULT_OVERLAP_TOKENS = 50;

    private final ChunkingStrategy chunker;
    private final EmbeddingService embedding;
    private final QdrantStore store;
    private final MemoryChunkRepository chunks;
    private final MeterRegistry meters;

    public MemoryIngestionService(
            ChunkingStrategy chunker,
            EmbeddingService embedding,
            QdrantStore store,
            MemoryChunkRepository chunks,
            MeterRegistry meters
    ) {
        this.chunker = chunker;
        this.embedding = embedding;
        this.store = store;
        this.chunks = chunks;
        this.meters = meters;
    }

    /**
     * Chunk → embed → index. Returns the chunk count + a list of created
     * row ids. The QdrantStore is collection-partitioned by the tenant
     * binding so cross-tenant recall remains impossible.
     */
    @Transactional
    public IngestResult ingest(String sourceUri, String sourceType, String text, UUID agentId) {
        if (text == null || text.isBlank()) {
            return new IngestResult(0, List.of());
        }
        final var tenantId = TenantContext.require();
        final var strategy = pickStrategy(sourceType);
        final var pieces = strategy.equals("semantic")
                ? chunker.semantic(text, DEFAULT_CHUNK_TOKENS)
                : strategy.equals("sentenceAware")
                    ? chunker.sentenceAware(text, DEFAULT_CHUNK_TOKENS)
                    : chunker.fixedWindow(text, DEFAULT_CHUNK_TOKENS, DEFAULT_OVERLAP_TOKENS);

        log.info("Ingesting {} chunks (strategy={}, tenant={}, source={})", pieces.size(), strategy, tenantId, sourceUri);
        final var ids = new java.util.ArrayList<UUID>(pieces.size());

        for (final var chunk : pieces) {
            final var pointId = store.upsert(
                    QdrantStore.newPointId(),
                    embedding.embed(chunk.text()),
                    Map.of(
                            "tenant_id", tenantId.toString(),
                            "source_uri", sourceUri == null ? "" : sourceUri,
                            "source_type", sourceType == null ? "text" : sourceType,
                            "chunk_index", chunk.index(),
                            "text", chunk.text()
                    )
            );
            final var row = new MemoryChunk();
            row.setTenantId(tenantId);
            row.setAgentId(agentId);
            row.setSourceUri(sourceUri);
            row.setSourceType(sourceType == null ? "text" : sourceType);
            row.setChunkIndex(chunk.index());
            row.setText(chunk.text());
            row.setTokenCount(chunk.approximateTokens());
            row.setQdrantPointId(pointId);
            final var metadata = new LinkedHashMap<String, Object>();
            metadata.put("strategy", strategy);
            metadata.put("startChar", chunk.startChar());
            metadata.put("endChar", chunk.endChar());
            row.setMetadata(metadata);
            chunks.save(row);
            ids.add(row.getId());
        }

        meters.counter("nexus.memory.ingested_chunks", "strategy", strategy).increment(pieces.size());
        return new IngestResult(pieces.size(), ids);
    }

    private static String pickStrategy(String sourceType) {
        if (sourceType == null) return "fixedWindow";
        final var t = sourceType.toLowerCase();
        if (t.contains("code") || t.contains("source")) return "semantic";
        if (t.contains("doc") || t.contains("article") || t.contains("narrative")) return "sentenceAware";
        return "fixedWindow";
    }

    public record IngestResult(int chunkCount, List<UUID> chunkIds) {}
}

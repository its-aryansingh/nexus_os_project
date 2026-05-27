package com.nexus.os.api.controller;

import com.nexus.os.agents.rag.MemoryIngestionService;
import com.nexus.os.agents.rag.Retriever;
import com.nexus.os.domain.MemoryChunk;
import com.nexus.os.domain.MemoryChunkRepository;
import com.nexus.os.observability.AuditLogger;
import com.nexus.os.tenancy.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemoryIngestionService ingestion;
    private final MemoryChunkRepository chunks;
    private final Retriever retriever;
    private final AuditLogger audit;

    public MemoryController(
            MemoryIngestionService ingestion,
            MemoryChunkRepository chunks,
            Retriever retriever,
            AuditLogger audit
    ) {
        this.ingestion = ingestion;
        this.chunks = chunks;
        this.retriever = retriever;
        this.audit = audit;
    }

    public record IngestRequest(String sourceUri, String sourceType, String text, UUID agentId) {}
    public record SearchRequest(String query, Integer topK) {}

    /** Recent chunks for the active tenant — feeds the /memory page. */
    @GetMapping
    public Page<MemoryChunk> recent(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return chunks.findByTenantIdOrderByCreatedAtDesc(TenantContext.require(), PageRequest.of(page, size));
    }

    /** Chunk -> embed -> upsert into the tenant collection. */
    @PostMapping("/ingest")
    public MemoryIngestionService.IngestResult ingest(@RequestBody IngestRequest req) {
        final var result = ingestion.ingest(req.sourceUri(), req.sourceType(), req.text(), req.agentId());
        audit.log("memory.ingested", Map.of(
                "sourceUri", req.sourceUri() == null ? "" : req.sourceUri(),
                "sourceType", req.sourceType() == null ? "text" : req.sourceType(),
                "chunkCount", result.chunkCount()
        ));
        return result;
    }

    /** Vector search — useful for sanity-checking ingestion + debug. */
    @PostMapping("/search")
    public List<Retriever.Context> search(@RequestBody SearchRequest req) {
        final var topK = req.topK() == null ? 5 : Math.min(20, Math.max(1, req.topK()));
        return retriever.retrieve(req.query(), topK);
    }
}

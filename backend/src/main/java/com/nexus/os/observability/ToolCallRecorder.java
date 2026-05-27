package com.nexus.os.observability;

import com.nexus.os.domain.ToolCallEntity;
import com.nexus.os.domain.ToolCallRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists a row in {@code tool_calls} for every internal "tool" / specialist
 * invocation. Read back by {@code WorkflowController.runs/{id}/tools} (v0.3)
 * to render the per-run timeline.
 *
 * <p>Wrapped with REQUIRES_NEW so a failure to record audit doesn't roll
 * back the calling business transaction. The DLQ/audit channel is more
 * forgiving than the cost ledger.
 */
@Service
public class ToolCallRecorder {

    private static final Logger log = LoggerFactory.getLogger(ToolCallRecorder.class);

    private final ToolCallRepository repo;
    private final MeterRegistry meters;

    public ToolCallRecorder(ToolCallRepository repo, MeterRegistry meters) {
        this.repo = repo;
        this.meters = meters;
    }

    @Transactional
    public UUID record(
            UUID tenantId,
            UUID workflowRunId,
            UUID agentId,
            String toolName,
            Map<String, Object> arguments,
            Map<String, Object> result,
            String error,
            long durationMs
    ) {
        try {
            final var row = new ToolCallEntity();
            row.setTenantId(tenantId);
            row.setWorkflowRunId(workflowRunId);
            row.setAgentId(agentId);
            row.setToolName(toolName);
            row.setArguments(arguments == null ? Map.of() : new LinkedHashMap<>(arguments));
            row.setResult(result == null ? null : new LinkedHashMap<>(result));
            row.setError(error);
            row.setDurationMs((int) Math.min(Integer.MAX_VALUE, durationMs));
            repo.save(row);

            meters.counter("nexus.tool_calls.recorded", "tool", toolName,
                    "outcome", error == null ? "ok" : "error").increment();
            return row.getId();
        } catch (RuntimeException recordFail) {
            // Never let an audit-write failure surface as a user-visible error.
            log.warn("ToolCallRecorder failed for tool={}: {}", toolName, recordFail.getMessage());
            meters.counter("nexus.tool_calls.record_failed").increment();
            return null;
        }
    }
}

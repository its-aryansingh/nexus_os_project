package com.nexus.os.temporal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.os.domain.WorkflowRun;
import com.nexus.os.domain.WorkflowRunRepository;
import com.nexus.os.temporal.workflows.AgentOrchestrationWorkflow;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Single seam for kicking off durable agent workflows. Persists a
 * {@link WorkflowRun} row in the CQRS read model before submitting the
 * workflow to Temporal — so the dashboard's run list reflects the new
 * execution even before it completes.
 */
@Component
public class WorkflowStarter {

    private static final Logger log = LoggerFactory.getLogger(WorkflowStarter.class);

    private final WorkflowClient workflowClient;
    private final WorkflowRunRepository runs;
    private final ObjectMapper objectMapper;
    private final String taskQueue;
    private final Counter startedCounter;

    public WorkflowStarter(
            WorkflowClient workflowClient,
            WorkflowRunRepository runs,
            ObjectMapper objectMapper,
            @Value("${temporal.task-queue:nexus-default-queue}") String taskQueue,
            MeterRegistry meters
    ) {
        this.workflowClient = workflowClient;
        this.runs = runs;
        this.objectMapper = objectMapper;
        this.taskQueue = taskQueue;
        this.startedCounter = Counter.builder("nexus.workflow.runs")
                .description("Number of agent workflows started")
                .tag("status", "started")
                .register(meters);
    }

    /**
     * Start an agent-orchestration workflow for the given tenant + payload.
     * Returns the workflow id so callers can correlate.
     */
    @Transactional
    public String startAgentOrchestration(UUID tenantId, UUID workflowDefinitionId, Map<String, Object> payload) {
        final var workflowId = "nexus-orch-" + UUID.randomUUID();

        // Persist the read-model row BEFORE submission so the UI reflects it
        // even if the worker is slow to pick the workflow up.
        final var run = new WorkflowRun();
        run.setTenantId(tenantId);
        run.setWorkflowId(workflowDefinitionId);
        run.setTemporalWorkflowId(workflowId);
        run.setTemporalRunId("pending");
        run.setStatus(WorkflowRun.Status.running);

        // Enrich payload with tenant + workflow_run_id for the activity layer.
        // workflow_run_id is essential for the completion projector to update
        // the right row when the activity finishes.
        final var enriched = new LinkedHashMap<String, Object>();
        enriched.put("tenant_id", tenantId.toString());
        if (payload != null) enriched.putAll(payload);
        run.setInputPayload(enriched);
        runs.save(run);
        // workflow_run_id added AFTER save so the row has its id assigned by JPA
        enriched.put("workflow_run_id", run.getId().toString());

        final var payloadJson = serialize(enriched);
        final var stub = workflowClient.newWorkflowStub(
                AgentOrchestrationWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(taskQueue)
                        .setWorkflowId(workflowId)
                        .setWorkflowExecutionTimeout(Duration.ofMinutes(10))
                        .setWorkflowRunTimeout(Duration.ofMinutes(5))
                        .build()
        );

        try {
            // Async submission — WorkflowClient.start() returns immediately
            // and the workflow runs on the worker. AgentActivityImpl emits
            // a workflow.run.completed event on the final activity which
            // WorkflowEventProjector consumes to update this row.
            final var execution = WorkflowClient.start(stub::orchestrate, payloadJson);
            run.setTemporalRunId(execution.getRunId());
            runs.save(run);
            startedCounter.increment();
            log.info("Started workflow {} for tenant {} (run {} -> temporal run {})",
                    workflowId, tenantId, run.getId(), execution.getRunId());
            return workflowId;
        } catch (RuntimeException submitFailed) {
            run.setStatus(WorkflowRun.Status.failed);
            run.setError("submission failed: " + submitFailed.getMessage());
            runs.save(run);
            log.error("Workflow submission failed for tenant {}: {}", tenantId, submitFailed.getMessage(), submitFailed);
            throw submitFailed;
        }
    }

    private String serialize(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Failed to serialize workflow payload", impossible);
        }
    }
}

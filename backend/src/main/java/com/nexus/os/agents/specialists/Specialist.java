package com.nexus.os.agents.specialists;

import com.nexus.os.agents.NexusAgent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A bounded role-played sub-agent. The {@link Orchestrator} delegates each
 * request to the specialist whose {@link #handledIntents()} matches.
 *
 * <p>Specialists are intentionally narrow — Researcher does RAG, Copywriter
 * drafts content, Analyst answers structured questions, Reviewer
 * tribunal-votes on high-stakes outputs. Each one is auditable as a
 * separate tool_call row.
 */
public interface Specialist {

    /** Stable identifier (e.g. {@code researcher}). Used as the {@code tool_calls.tool_name}. */
    String name();

    /** Canonical intent labels this specialist handles. */
    Set<String> handledIntents();

    /** Run the specialist. The activity-layer wrapper provides the LLM seam. */
    Result handle(Request req, NexusAgent agent);

    /** Inputs to a specialist invocation. */
    record Request(
            UUID tenantId,
            UUID workflowRunId,
            String prompt,
            String intent,
            Map<String, Object> hints
    ) {}

    /** Output of a specialist invocation. */
    record Result(
            String text,
            String model,
            double temperature,
            int tokensIn,
            int tokensOut,
            Map<String, Object> metadata
    ) {}
}

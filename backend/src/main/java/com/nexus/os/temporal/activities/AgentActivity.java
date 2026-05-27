package com.nexus.os.temporal.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Temporal activity interface — defines the discrete units of work
 * within the {@link com.nexus.os.temporal.workflows.AgentOrchestrationWorkflow}.
 */
@ActivityInterface
public interface AgentActivity {

    /**
     * Classify the user's intent from the raw request payload.
     *
     * @param requestPayload JSON-serialized user request
     * @return a canonical intent string (e.g. "customer_support", "code_generation")
     */
    @ActivityMethod
    String classifyIntent(String requestPayload);

    /**
     * Route the classified intent to the best-fit agent.
     *
     * @param intent the classified intent
     * @return the agent identifier
     */
    @ActivityMethod
    String routeToAgent(String intent);

    /**
     * Execute the agent task and return the response.
     *
     * @param agentId        the selected agent's identifier
     * @param requestPayload the original user request
     * @return the agent's response
     */
    @ActivityMethod
    String executeAgentTask(String agentId, String requestPayload);

    /**
     * Execute via the Specialist Orchestrator. The intent is forwarded to
     * {@code Orchestrator.pick(intent)} which routes to the matching
     * specialist (Researcher / Copywriter / Analyst / Reviewer). Prefer
     * this over {@link #executeAgentTask} for new workflows.
     *
     * @param agentId        the selected agent's identifier (for audit trail)
     * @param intent         canonical intent string from {@link #classifyIntent}
     * @param requestPayload the original user request (JSON-serialized)
     * @return the specialist's response
     */
    @ActivityMethod
    String executeWithIntent(String agentId, String intent, String requestPayload);
}

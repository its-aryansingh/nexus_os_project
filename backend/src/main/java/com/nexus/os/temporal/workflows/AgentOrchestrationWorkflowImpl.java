package com.nexus.os.temporal.workflows;

import com.nexus.os.temporal.activities.AgentActivity;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;

/**
 * Implementation of the agent orchestration workflow.
 *
 * <p>Pipeline stages:
 * <ol>
 *   <li>Classify the inbound intent</li>
 *   <li>Route to the best-fit AI agent</li>
 *   <li>Execute the agent task</li>
 * </ol>
 *
 * <p>Each stage is a Temporal activity with independent retry semantics.
 */
public class AgentOrchestrationWorkflowImpl implements AgentOrchestrationWorkflow {

    private static final Logger log = Workflow.getLogger(AgentOrchestrationWorkflowImpl.class);

    private final AgentActivity activities = Workflow.newActivityStub(
            AgentActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(2))
                    .setRetryOptions(
                            RetryOptions.newBuilder()
                                    .setInitialInterval(Duration.ofSeconds(1))
                                    .setMaximumAttempts(3)
                                    .setBackoffCoefficient(2.0)
                                    .build()
                    )
                    .build()
    );

    @Override
    public String orchestrate(String requestPayload) {
        log.info("▶ Workflow started — classifying intent");

        // Stage 1: Classify the user's intent
        String intent = activities.classifyIntent(requestPayload);
        log.info("  Intent classified: {}", intent);

        // Stage 2: Route to the appropriate agent (audit-trail only — the
        // actual specialist is chosen by the Orchestrator inside the activity).
        String agentId = activities.routeToAgent(intent);
        log.info("  Routed to agent: {}", agentId);

        // Stage 3: Execute via the Specialist Orchestrator. Passing the
        // classified intent lets the Orchestrator pick the matching
        // specialist (Researcher / Copywriter / Analyst / Reviewer).
        String response = activities.executeWithIntent(agentId, intent, requestPayload);
        log.info("✔ Workflow complete — response length: {}", response.length());

        return response;
    }
}

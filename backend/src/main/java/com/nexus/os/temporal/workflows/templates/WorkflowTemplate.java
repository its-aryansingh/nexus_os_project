package com.nexus.os.temporal.workflows.templates;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static catalog of pre-built workflow templates the Agent Studio offers
 * as starting points. Each template carries:
 * <ul>
 *   <li>{@code slug} — stable identifier matching the seeded
 *       {@link com.nexus.os.domain.Workflow} row.</li>
 *   <li>{@code name} / {@code description} — UI strings.</li>
 *   <li>{@code temporalWorkflowType} — class name of the Temporal workflow
 *       the orchestrator instantiates. v0.2 all map to
 *       {@code AgentOrchestrationWorkflow}; v0.3 introduces specialised
 *       workflow classes for the more complex sagas.</li>
 *   <li>{@code definition} — React Flow graph (nodes + edges) rendered by
 *       the Studio canvas.</li>
 * </ul>
 *
 * <p>This is a static catalog deliberately — templates change rarely and
 * shipping them in code makes the surface tree-shake-able + reviewable.
 * Custom user-authored workflows still live in the {@code workflows}
 * table via the regular create API.
 */
public record WorkflowTemplate(
        String slug,
        String name,
        String description,
        String temporalWorkflowType,
        Map<String, Object> definition,
        List<String> tags
) {

    public static List<WorkflowTemplate> catalog() {
        return List.of(
                defaultOrchestration(),
                supportTriage(),
                contentPipeline(),
                researchSaga(),
                complianceReview()
        );
    }

    private static WorkflowTemplate defaultOrchestration() {
        return new WorkflowTemplate(
                "default-orchestration",
                "Default Orchestration",
                "Classify intent → route → execute. The v0.1 baseline saga. Best entry point for ad-hoc chat.",
                "AgentOrchestrationWorkflow",
                graph(
                        node("classify", "activity", "Classify Intent",        "agent-router", 80,  120),
                        node("route",    "activity", "Route to Specialist",    "agent-router", 320, 120),
                        node("execute",  "activity", "Execute (Orchestrator)", "specialist",   560, 120),
                        edge("classify", "route"),
                        edge("route",    "execute")
                ),
                List.of("baseline", "chat")
        );
    }

    private static WorkflowTemplate supportTriage() {
        return new WorkflowTemplate(
                "support-triage",
                "Support Triage",
                "Inbound message → urgency check → either auto-reply via Analyst or escalate to human review.",
                "AgentOrchestrationWorkflow",
                graph(
                        node("ingest",      "activity", "Ingest Message",        "channel",       60,  140),
                        node("urgency",     "activity", "Urgency Classifier",    "analyst",       300, 140),
                        node("decision",    "decision", "Auto-reply or Human?",  "decision",      560, 140),
                        node("analyst",     "specialist", "Analyst — auto reply", "analyst",      820, 60),
                        node("review",      "specialist", "Reviewer — escalate", "reviewer",      820, 220),
                        edge("ingest",   "urgency"),
                        edge("urgency",  "decision"),
                        edge("decision", "analyst",  "low urgency"),
                        edge("decision", "review",   "high urgency")
                ),
                List.of("support", "triage")
        );
    }

    private static WorkflowTemplate contentPipeline() {
        return new WorkflowTemplate(
                "content-pipeline",
                "Content Pipeline",
                "Researcher gathers RAG context → Copywriter drafts → Reviewer tribunal-approves before publish.",
                "AgentOrchestrationWorkflow",
                graph(
                        node("brief",       "input",      "Brief",                 "input",      60,  140),
                        node("researcher",  "specialist", "Researcher (RAG)",      "researcher", 300, 140),
                        node("copywriter",  "specialist", "Copywriter",            "copywriter", 560, 140),
                        node("reviewer",    "specialist", "Reviewer (Tribunal)",   "reviewer",   820, 140),
                        node("publish",     "output",     "Publish",               "output",    1080, 140),
                        edge("brief",      "researcher"),
                        edge("researcher", "copywriter"),
                        edge("copywriter", "reviewer"),
                        edge("reviewer",   "publish",  "APPROVE")
                ),
                List.of("content", "tribunal")
        );
    }

    private static WorkflowTemplate researchSaga() {
        return new WorkflowTemplate(
                "research-saga",
                "Research Saga",
                "Parallel Researcher fan-out across 3 sub-queries → Analyst synthesizes → final brief. Sagasaga-style compensation on partial failure.",
                "AgentOrchestrationWorkflow",
                graph(
                        node("topic",       "input",      "Topic",                "input",     60,  240),
                        node("r1",          "specialist", "Researcher · slice 1", "researcher", 300, 80),
                        node("r2",          "specialist", "Researcher · slice 2", "researcher", 300, 240),
                        node("r3",          "specialist", "Researcher · slice 3", "researcher", 300, 400),
                        node("analyst",     "specialist", "Analyst — synthesize", "analyst",    600, 240),
                        node("brief",       "output",     "Final Brief",          "output",     900, 240),
                        edge("topic", "r1"),
                        edge("topic", "r2"),
                        edge("topic", "r3"),
                        edge("r1", "analyst"),
                        edge("r2", "analyst"),
                        edge("r3", "analyst"),
                        edge("analyst", "brief")
                ),
                List.of("research", "parallel", "saga")
        );
    }

    private static WorkflowTemplate complianceReview() {
        return new WorkflowTemplate(
                "compliance-review",
                "Compliance Review (high-stakes)",
                "Tribunal-based pass/fail review with mandatory human escalation on disagreement above threshold.",
                "AgentOrchestrationWorkflow",
                graph(
                        node("input",     "input",      "Material to review",    "input",      60,  140),
                        node("policy",    "activity",   "Load policy context",   "policy",     300, 140),
                        node("tribunal",  "specialist", "Reviewer (Tribunal N=3)", "reviewer", 560, 140),
                        node("gate",      "decision",   "Unanimous?",            "decision",   820, 140),
                        node("approve",   "output",     "Approve",               "output",    1080, 60),
                        node("escalate",  "output",     "Escalate to human",     "output",    1080, 220),
                        edge("input",    "policy"),
                        edge("policy",   "tribunal"),
                        edge("tribunal", "gate"),
                        edge("gate",     "approve",  "all 3 agree"),
                        edge("gate",     "escalate", "split vote")
                ),
                List.of("compliance", "tribunal", "high-stakes")
        );
    }

    // ── builder helpers ─────────────────────────────────────────────────────

    private static Map<String, Object> graph(Object... pieces) {
        final var nodes = new java.util.ArrayList<Map<String, Object>>();
        final var edges = new java.util.ArrayList<Map<String, Object>>();
        for (final var p : pieces) {
            if (p instanceof Map<?, ?> m && "edge".equals(m.get("kind"))) {
                @SuppressWarnings("unchecked") final var em = (Map<String, Object>) m;
                edges.add(em);
            } else if (p instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked") final var nm = (Map<String, Object>) m;
                nodes.add(nm);
            }
        }
        final var graph = new LinkedHashMap<String, Object>();
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        return graph;
    }

    private static Map<String, Object> node(String id, String type, String label, String role, int x, int y) {
        final var n = new LinkedHashMap<String, Object>();
        n.put("id", id);
        n.put("type", type);
        n.put("role", role);
        n.put("data", Map.of("label", label));
        n.put("position", Map.of("x", x, "y", y));
        return n;
    }

    private static Map<String, Object> edge(String from, String to) {
        return edge(from, to, null);
    }

    private static Map<String, Object> edge(String from, String to, String label) {
        final var e = new LinkedHashMap<String, Object>();
        e.put("kind", "edge");
        e.put("id", "e-" + from + "-" + to);
        e.put("source", from);
        e.put("target", to);
        if (label != null) e.put("label", label);
        return e;
    }
}

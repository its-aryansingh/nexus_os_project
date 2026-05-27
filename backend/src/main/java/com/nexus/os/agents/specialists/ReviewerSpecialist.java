package com.nexus.os.agents.specialists;

import com.nexus.os.agents.AgentCapability;
import com.nexus.os.agents.ModelRouter;
import com.nexus.os.agents.NexusAgent;
import com.nexus.os.agents.Tribunal;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * High-stakes voting. Runs the prompt through {@link Tribunal} — N
 * independent jurors with mild seed variance (achieved through temperature
 * variation), majority wins, disagreement flagged on the
 * {@code nexus_tribunal_disagreement} metric.
 *
 * <p>Use cases the orchestrator routes here: "approve/reject", "review",
 * compliance check, send-real-money decisions. The disagreement signal
 * surfaces uncertainty to the dashboard so a human can intervene.
 */
@Component
public class ReviewerSpecialist implements Specialist {

    private static final double[] JUROR_TEMPS = { 0.2, 0.5, 0.8 };

    private final Tribunal tribunal;
    private final ModelRouter modelRouter;

    public ReviewerSpecialist(Tribunal tribunal, ModelRouter modelRouter) {
        this.tribunal = tribunal;
        this.modelRouter = modelRouter;
    }

    @Override
    public String name() { return "reviewer"; }

    @Override
    public Set<String> handledIntents() {
        return Set.of("review", "approve", "compliance_check", "high_stakes");
    }

    @Override
    public Result handle(Request req, NexusAgent agent) {
        final var model = modelRouter.getExpensiveModel();
        final var framed = """
                You are Reviewer — a Nexus OS specialist on a 3-juror tribunal.
                Reply with exactly one word: APPROVE, REJECT, or ESCALATE.
                Base your verdict on accuracy and compliance.

                Subject:
                %s
                """.formatted(req.prompt());

        final var jurorIdx = new int[]{0};
        final var verdict = tribunal.vote(() -> {
            final var temp = JUROR_TEMPS[jurorIdx[0]++ % JUROR_TEMPS.length];
            return agent.execute(new AgentCapability.TextGeneration(model, temp), framed).trim().toUpperCase();
        });

        final var meta = new LinkedHashMap<String, Object>();
        meta.put("specialist", name());
        meta.put("majority", verdict.majority());
        meta.put("majority_count", verdict.majorityCount());
        meta.put("disagreement", verdict.disagreement());
        meta.put("distinct_answers", verdict.distinctAnswers());
        meta.put("unanimous", verdict.unanimous());

        // Approximate cost — N invocations of the framed prompt.
        final var tokensIn = estimate(framed) * verdict.totalJurors();
        final var tokensOut = estimate(verdict.majority()) * verdict.totalJurors();
        return new Result(verdict.majority(), model, 0.5, tokensIn, tokensOut, meta);
    }

    private static int estimate(String s) { return s == null ? 0 : Math.max(1, s.length() / 4); }
}

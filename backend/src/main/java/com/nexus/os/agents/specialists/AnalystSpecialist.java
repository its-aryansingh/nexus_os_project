package com.nexus.os.agents.specialists;

import com.nexus.os.agents.AgentCapability;
import com.nexus.os.agents.ModelRouter;
import com.nexus.os.agents.NexusAgent;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Structured-output answers. Cheap model, low temperature — predictability
 * matters more than flair. Asks the model to respond with concise bullets
 * so downstream processors can parse cleanly.
 */
@Component
public class AnalystSpecialist implements Specialist {

    private final ModelRouter modelRouter;

    public AnalystSpecialist(ModelRouter modelRouter) {
        this.modelRouter = modelRouter;
    }

    @Override
    public String name() { return "analyst"; }

    @Override
    public Set<String> handledIntents() {
        return Set.of("compute", "extract", "classify", "tabulate");
    }

    @Override
    public Result handle(Request req, NexusAgent agent) {
        final var model = modelRouter.getCheapModel();
        final var temp = 0.1;
        final var framed = """
                You are Analyst — a Nexus OS specialist that gives structured, precise answers.
                Reply with bullet points only. No prose. Numbers where possible.

                Question:
                %s
                """.formatted(req.prompt());
        final var response = agent.execute(new AgentCapability.TextGeneration(model, temp), framed);
        return new Result(response, model, temp, estimate(framed), estimate(response),
                Map.of("specialist", name(), "format", "bullets"));
    }

    private static int estimate(String s) { return s == null ? 0 : Math.max(1, s.length() / 4); }
}

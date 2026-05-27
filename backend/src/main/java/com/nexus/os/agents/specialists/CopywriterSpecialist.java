package com.nexus.os.agents.specialists;

import com.nexus.os.agents.AgentCapability;
import com.nexus.os.agents.ModelRouter;
import com.nexus.os.agents.NexusAgent;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Drafts content. Uses the expensive model with a higher temperature — we
 * want creative prose, not deterministic output. Wraps the prompt with a
 * persona instruction that the upstream HallucinationGuard length-sanity
 * check naturally validates.
 */
@Component
public class CopywriterSpecialist implements Specialist {

    private final ModelRouter modelRouter;

    public CopywriterSpecialist(ModelRouter modelRouter) {
        this.modelRouter = modelRouter;
    }

    @Override
    public String name() { return "copywriter"; }

    @Override
    public Set<String> handledIntents() {
        return Set.of("content_creation", "draft", "compose", "rewrite");
    }

    @Override
    public Result handle(Request req, NexusAgent agent) {
        final var model = modelRouter.getExpensiveModel();
        final var temp = 0.8;
        final var framed = """
                You are Copywriter — a Nexus OS specialist that drafts polished, on-brand text.
                Write clearly. Open with the value proposition. Avoid filler.

                Brief:
                %s
                """.formatted(req.prompt());
        final var response = agent.execute(new AgentCapability.TextGeneration(model, temp), framed);
        return new Result(response, model, temp, estimate(framed), estimate(response),
                Map.of("specialist", name()));
    }

    private static int estimate(String s) { return s == null ? 0 : Math.max(1, s.length() / 4); }
}

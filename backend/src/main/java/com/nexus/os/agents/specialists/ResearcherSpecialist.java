package com.nexus.os.agents.specialists;

import com.nexus.os.agents.AgentCapability;
import com.nexus.os.agents.ModelRouter;
import com.nexus.os.agents.NexusAgent;
import com.nexus.os.agents.rag.Retriever;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * RAG-augmented Q&A. Pulls top-5 contexts from the caller's Qdrant
 * collection and feeds them to a cheap-model prompt with citation
 * requirements. Returns answer + cited [n] markers so the
 * HallucinationGuard citation check can validate.
 */
@Component
public class ResearcherSpecialist implements Specialist {

    private static final int TOP_K = 5;

    private final Retriever retriever;
    private final ModelRouter modelRouter;

    public ResearcherSpecialist(Retriever retriever, ModelRouter modelRouter) {
        this.retriever = retriever;
        this.modelRouter = modelRouter;
    }

    @Override
    public String name() { return "researcher"; }

    @Override
    public Set<String> handledIntents() {
        return Set.of("data_analysis", "research", "summarize", "lookup");
    }

    @Override
    public Result handle(Request req, NexusAgent agent) {
        final var contexts = retriever.retrieve(req.prompt(), TOP_K);
        final var promptWithCtx = retriever.renderPrompt(contexts, req.prompt());
        final var model = modelRouter.getCheapModel();
        final var response = agent.execute(new AgentCapability.TextGeneration(model, 0.2), promptWithCtx);
        final var meta = new LinkedHashMap<String, Object>();
        meta.put("contexts_used", contexts.size());
        meta.put("specialist", name());
        return new Result(response, model, 0.2, estimate(promptWithCtx), estimate(response), meta);
    }

    private static int estimate(String s) { return s == null ? 0 : Math.max(1, s.length() / 4); }
}

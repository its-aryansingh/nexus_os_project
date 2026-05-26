package com.nexus.os.agents;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runtime AI agent powered by LangChain4j. The {@link ChatLanguageModel}
 * dependency is bound by {@link LlmConfig} — real OpenAI client when a key
 * is configured, deterministic {@link MockChatLanguageModel} otherwise.
 *
 * <p>Uses Java 21 pattern matching on {@link AgentCapability} to route
 * requests to the appropriate handler. Side effects (cost recording,
 * cache lookup, hallucination guard, tribunal voting) live in
 * {@link com.nexus.os.temporal.activities.AgentActivityImpl} — this class
 * is the LLM-call seam only.
 */
@Component
public class NexusAgent {

    private static final Logger log = LoggerFactory.getLogger(NexusAgent.class);

    private final ChatLanguageModel chatModel;

    public NexusAgent(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * Execute an agent task based on the given capability and user prompt.
     */
    public String execute(AgentCapability capability, String userPrompt) {
        log.debug("execute capability={}", capability.getClass().getSimpleName());

        return switch (capability) {
            case AgentCapability.TextGeneration tg -> {
                log.debug("TextGeneration model={} temp={}", tg.modelId(), tg.temperature());
                yield chatModel.generate(userPrompt);
            }
            case AgentCapability.CodeExecution ce -> {
                log.debug("CodeExecution lang={} timeout={}ms", ce.language(), ce.timeoutMs());
                final var codePrompt = ("You are a code assistant. Language: %s. "
                        + "Respond with code only.%n%n%s").formatted(ce.language(), userPrompt);
                yield chatModel.generate(codePrompt);
            }
            case AgentCapability.DataRetrieval dr -> {
                log.debug("DataRetrieval collection={} topK={}", dr.collectionName(), dr.topK());
                // RAG retrieval happens in the activity layer (Retriever bean);
                // this branch is invoked AFTER context has been assembled.
                yield chatModel.generate("Answer using the provided context:%n%s".formatted(userPrompt));
            }
            case AgentCapability.ImageAnalysis ia -> {
                log.debug("ImageAnalysis model={} maxTokens={}", ia.modelId(), ia.maxTokens());
                // Vision model wiring lands in v0.2 (separate LangChain4j module).
                yield chatModel.generate("Describe the image context:%n%s".formatted(userPrompt));
            }
        };
    }
}

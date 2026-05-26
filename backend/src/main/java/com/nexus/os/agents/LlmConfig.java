package com.nexus.os.agents;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Picks the real OpenAI client when {@code LANGCHAIN4J_OPEN_AI_API_KEY} is
 * set, otherwise falls back to {@link MockChatLanguageModel}. Honours the
 * "demo-able without keys" design goal — the system boots and serves
 * tagged mock responses with zero credentials.
 *
 * <p>Set {@code LANGCHAIN4J_OPEN_AI_BASE_URL=http://localhost:11434/v1}
 * to point the OpenAI client at a local Ollama instance (BYOM).
 */
@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    @Primary
    public ChatLanguageModel chatLanguageModel(
            @Value("${langchain4j.open-ai.api-key:}") String apiKey,
            @Value("${langchain4j.open-ai.base-url:}") String baseUrl,
            @Value("${langchain4j.open-ai.model-name:gpt-4o-mini}") String modelName,
            @Value("${langchain4j.open-ai.temperature:0.7}") double temperature,
            @Value("${langchain4j.open-ai.max-tokens:2048}") int maxTokens
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("LLM api-key not set — using MockChatLanguageModel ({}). Real provider boots once you set LANGCHAIN4J_OPEN_AI_API_KEY.", modelName);
            return new MockChatLanguageModel(modelName);
        }
        log.info("LLM api-key present — using OpenAiChatModel (model={}, baseUrl={})",
                modelName, baseUrl == null || baseUrl.isBlank() ? "default" : baseUrl);
        final var builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }
}

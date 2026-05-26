package com.nexus.os.agents;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic LangChain4j chat model. Used when no LLM API key is set —
 * implements the "demo-able without keys" design goal from SYSTEM_DESIGN.md.
 *
 * <p>The response is intentionally tagged with {@code [demo-data]} so any
 * downstream UI / persistence can clearly distinguish mock output from real.
 */
public final class MockChatLanguageModel implements ChatLanguageModel {

    private final String modelName;
    private final AtomicLong serial = new AtomicLong();

    public MockChatLanguageModel(String modelName) {
        this.modelName = modelName;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        final var prompt = extractPrompt(messages);
        final var n = serial.incrementAndGet();
        final var text = mockBody(prompt, n);
        final var tokens = new TokenUsage(estimate(prompt), estimate(text));
        return Response.from(AiMessage.from(text), tokens);
    }

    @Override
    public String generate(String userMessage) {
        return mockBody(userMessage, serial.incrementAndGet());
    }

    private String mockBody(String prompt, long n) {
        final var head = prompt == null ? "" : prompt.strip();
        final var snippet = head.length() > 80 ? head.substring(0, 80) + "…" : head;
        return ("[demo-data] mock %s #%d. echo: \"%s\". "
                + "Replace LANGCHAIN4J_OPEN_AI_API_KEY in .env for real output.")
                .formatted(modelName, n, snippet);
    }

    private static String extractPrompt(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return "";
        // last user (or system) message is the cue
        for (int i = messages.size() - 1; i >= 0; i--) {
            final var m = messages.get(i);
            if (m instanceof UserMessage u) return u.singleText();
            if (m instanceof SystemMessage s) return s.text();
        }
        return messages.get(messages.size() - 1).toString();
    }

    private static int estimate(String s) {
        return s == null ? 0 : Math.max(1, s.length() / 4);   // ~4 chars/token rule of thumb
    }
}

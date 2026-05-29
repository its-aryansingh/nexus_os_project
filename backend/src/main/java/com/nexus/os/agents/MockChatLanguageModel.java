package com.nexus.os.agents;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic LangChain4j chat model. Used when no LLM API key is set —
 * implements the "demo-able without keys" design goal from SYSTEM_DESIGN.md.
 *
 * <p>The response is intentionally tagged with {@code [demo-data]} so any
 * downstream UI / persistence can clearly distinguish mock output from real.
 *
 * <p>When the prompt embeds an option list of the form
 * {@code "Reply with one of: A, B, C"} (case-insensitive, several phrasings
 * tolerated), the mock returns one of the listed tokens chosen
 * deterministically from {@code serial * primeStep}. This is what makes the
 * Tribunal end-to-end demo produce realistic juror variance — three jurors
 * see three different choices on every other run, exercising the
 * disagreement signal without any LLM keys.
 */
public final class MockChatLanguageModel implements ChatLanguageModel {

    private static final Pattern OPTION_LIST_RE = Pattern.compile(
            "(?:reply|respond|answer|choose|pick)\\s+with\\s+(?:exactly\\s+)?one\\s+(?:of|word|option)?\\s*[:=]?\\s*([A-Z0-9_,\\s/\\-]{3,200}?)(?:\\.|\\n|$)",
            Pattern.CASE_INSENSITIVE);

    private final String modelName;
    private final AtomicLong serial = new AtomicLong();

    public MockChatLanguageModel(String modelName) {
        this.modelName = modelName;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        final var prompt = extractPrompt(messages);
        final var n = serial.incrementAndGet();
        final var text = render(prompt, n);
        final var tokens = new TokenUsage(estimate(prompt), estimate(text));
        return Response.from(AiMessage.from(text), tokens);
    }

    @Override
    public String generate(String userMessage) {
        return render(userMessage, serial.incrementAndGet());
    }

    private String render(String prompt, long n) {
        final var choice = pickFromOptions(prompt, n);
        return choice != null ? choice : mockBody(prompt, n);
    }

    /**
     * Detect an option list in the prompt and return one of the options
     * deterministically. Returns {@code null} when no list is detected so
     * the caller falls back to the standard demo echo.
     */
    private String pickFromOptions(String prompt, long n) {
        if (prompt == null || prompt.isBlank()) return null;
        final Matcher m = OPTION_LIST_RE.matcher(prompt);
        if (!m.find()) return null;
        final var list = m.group(1);
        if (list == null) return null;

        final var options = new ArrayList<String>();
        for (final var raw : list.split("[,/]| or ")) {
            final var token = raw.trim().replaceAll("[\\.\\)\\(\"']", "");
            if (!token.isEmpty() && token.length() <= 30) {
                options.add(token.toUpperCase(Locale.ROOT));
            }
        }
        if (options.size() < 2) return null;

        // Mix serial with prompt hash so each juror call gets a stable but
        // distinct index — yields realistic disagreement on small panels.
        final int idx = Math.floorMod((int) (n * 1315423911L) ^ prompt.hashCode(), options.size());
        return options.get(idx);
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

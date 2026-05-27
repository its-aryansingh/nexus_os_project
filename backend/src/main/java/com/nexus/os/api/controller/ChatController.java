package com.nexus.os.api.controller;

import com.nexus.os.agents.AgentCapability;
import com.nexus.os.agents.NexusAgent;
import com.nexus.os.agents.specialists.Orchestrator;
import com.nexus.os.agents.specialists.Specialist;
import com.nexus.os.domain.ChatMessage;
import com.nexus.os.domain.ChatMessageRepository;
import com.nexus.os.domain.ChatSession;
import com.nexus.os.domain.ChatSessionRepository;
import com.nexus.os.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Multi-turn streaming chat. Accepts an optional {@code sessionId} so
 * conversations have continuity; if omitted, a new {@link ChatSession}
 * is created and its id returned via the {@code X-Session-Id} response
 * header (the frontend stashes it for follow-up turns).
 *
 * <p>Each turn writes a {@code chat_messages} row for the user input AND
 * a row for the agent response, with token + specialist + model metadata
 * captured. v0.3 reads these back to seed the LLM context window so the
 * model sees the actual conversation history.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final NexusAgent agent;
    private final Orchestrator orchestrator;
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;

    public ChatController(
            NexusAgent agent,
            Orchestrator orchestrator,
            ChatSessionRepository sessions,
            ChatMessageRepository messages
    ) {
        this.agent = agent;
        this.orchestrator = orchestrator;
        this.sessions = sessions;
        this.messages = messages;
    }

    public record ChatRequest(String prompt, String model, Double temperature, UUID sessionId, String intent) {}
    public record SessionSummary(UUID id, String title, OffsetDateTime lastMessageAt, long messageCount) {}

    // ── session management ──────────────────────────────────────────────────

    @GetMapping("/sessions")
    public List<SessionSummary> listSessions() {
        final var tenantId = TenantContext.require();
        return sessions.findByTenantIdOrderByLastMessageAtDesc(
                tenantId, org.springframework.data.domain.PageRequest.of(0, 50)
        ).map(s -> new SessionSummary(s.getId(), s.getTitle(), s.getLastMessageAt(), messages.countBySessionId(s.getId())))
                .getContent();
    }

    @GetMapping("/sessions/{id}/messages")
    public List<ChatMessage> sessionMessages(@PathVariable UUID id) {
        return messages.findBySessionIdOrderByOccurredAtAsc(id);
    }

    // ── streaming ───────────────────────────────────────────────────────────

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest req) {
        final var tenantId = TenantContext.require();
        final var emitter = new SseEmitter(60_000L);

        // 1. Resolve / create session synchronously so the response header is set.
        final var sessionId = ensureSession(tenantId, req).getId();
        emitter.onCompletion(() -> { /* no-op */ });

        // 2. Persist the user turn synchronously (so the row exists by the time
        //    the agent reply lands).
        persistMessage(tenantId, sessionId, ChatMessage.Role.user, req.prompt(),
                null, null, null, 0, 0, Map.of());

        // 3. Dispatch the LLM call on a virtual thread.
        Thread.startVirtualThread(() -> {
            TenantContext.set(tenantId);
            try {
                final var intent = req.intent() == null || req.intent().isBlank() ? "general_chat" : req.intent();
                final var specialist = orchestrator.pick(intent);
                final var sresult = specialist.handle(
                        new Specialist.Request(tenantId, null, req.prompt(), intent, Map.of()),
                        agent
                );
                emitter.send(SseEmitter.event().name("token").data(sresult.text()));
                emitter.send(SseEmitter.event()
                        .name("meta")
                        .data(Map.of(
                                "session_id", sessionId,
                                "specialist", specialist.name(),
                                "model", sresult.model(),
                                "tokens_in", sresult.tokensIn(),
                                "tokens_out", sresult.tokensOut()
                        )));
                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();

                persistMessage(tenantId, sessionId, ChatMessage.Role.agent, sresult.text(),
                        null, specialist.name(), sresult.model(),
                        sresult.tokensIn(), sresult.tokensOut(), sresult.metadata());
                touchSession(sessionId);
            } catch (IOException io) {
                emitter.completeWithError(io);
            } catch (Exception fail) {
                log.warn("chat stream failed: {}", fail.getMessage(), fail);
                try {
                    emitter.send(SseEmitter.event().name("error").data(fail.getMessage()));
                } catch (IOException ignored) {
                    // emitter likely disconnected
                }
                emitter.completeWithError(fail);
            } finally {
                TenantContext.clear();
            }
        });
        return emitter;
    }

    // ── internals ───────────────────────────────────────────────────────────

    @Transactional
    protected ChatSession ensureSession(UUID tenantId, ChatRequest req) {
        if (req.sessionId() != null) {
            final var existing = sessions.findById(req.sessionId()).orElse(null);
            if (existing != null) return existing;
        }
        final var s = new ChatSession();
        s.setTenantId(tenantId);
        s.setTitle(deriveTitle(req.prompt()));
        return sessions.save(s);
    }

    @Transactional
    protected void persistMessage(
            UUID tenantId, UUID sessionId, ChatMessage.Role role, String content,
            UUID workflowRunId, String specialist, String model,
            int tokensIn, int tokensOut, Map<String, Object> metadata
    ) {
        final var m = new ChatMessage();
        m.setTenantId(tenantId);
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content == null ? "" : content);
        m.setWorkflowRunId(workflowRunId);
        m.setSpecialist(specialist);
        m.setModel(model);
        m.setTokensIn(tokensIn);
        m.setTokensOut(tokensOut);
        m.setMetadata(metadata == null ? Map.of() : new LinkedHashMap<>(metadata));
        messages.save(m);
    }

    @Transactional
    protected void touchSession(UUID sessionId) {
        sessions.findById(sessionId).ifPresent(s -> {
            s.setLastMessageAt(OffsetDateTime.now());
            sessions.save(s);
        });
    }

    private static String deriveTitle(String prompt) {
        if (prompt == null || prompt.isBlank()) return "New chat";
        final var trimmed = prompt.strip().replaceAll("\\s+", " ");
        return trimmed.length() > 60 ? trimmed.substring(0, 57) + "…" : trimmed;
    }
}

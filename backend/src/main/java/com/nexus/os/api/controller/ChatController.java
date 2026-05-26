package com.nexus.os.api.controller;

import com.nexus.os.agents.AgentCapability;
import com.nexus.os.agents.NexusAgent;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * Streaming chat endpoint. v0.1 returns the full completion as a single SSE
 * event after the underlying {@code NexusAgent.execute} call resolves; v0.2
 * pipes per-token streaming once we add the LangChain4j StreamingChatModel.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final NexusAgent agent;

    public ChatController(NexusAgent agent) {
        this.agent = agent;
    }

    public record ChatRequest(String prompt, String model, Double temperature) {}

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest req) {
        final var emitter = new SseEmitter(60_000L);

        // run async — Spring's SseEmitter requires send() off the request thread
        Thread.startVirtualThread(() -> {
            try {
                final var capability = new AgentCapability.TextGeneration(
                        req.model() != null ? req.model() : "gpt-4o-mini",
                        req.temperature() != null ? req.temperature() : 0.7
                );
                final var response = agent.execute(capability, req.prompt());
                emitter.send(SseEmitter.event().name("token").data(response));
                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();
            } catch (IOException io) {
                emitter.completeWithError(io);
            } catch (Exception fail) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(fail.getMessage()));
                } catch (IOException ignored) {
                    // emitter likely already disconnected
                }
                emitter.completeWithError(fail);
            }
        });
        return emitter;
    }
}

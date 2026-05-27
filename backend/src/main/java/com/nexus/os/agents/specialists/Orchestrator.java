package com.nexus.os.agents.specialists;

import com.nexus.os.agents.NexusAgent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Picks the right {@link Specialist} for an intent and dispatches. Order
 * of the injected list dictates priority — Spring injects in declared bean
 * order so we rely on {@link org.springframework.core.annotation.Order} on
 * specialists when finer control is needed (none today; we keep deterministic
 * iteration via the by-name map).
 */
@Component
public class Orchestrator {

    private static final Logger log = LoggerFactory.getLogger(Orchestrator.class);

    private final Map<String, Specialist> byName;
    private final List<Specialist> all;
    private final Specialist fallback;
    private final MeterRegistry meters;

    public Orchestrator(List<Specialist> specialists, MeterRegistry meters) {
        if (specialists == null || specialists.isEmpty()) {
            throw new IllegalStateException("Orchestrator requires at least one Specialist bean");
        }
        this.all = List.copyOf(specialists);
        this.byName = specialists.stream().collect(Collectors.toUnmodifiableMap(Specialist::name, s -> s));
        // Analyst is the safest default — cheap, structured, no side effects.
        this.fallback = byName.getOrDefault("analyst", all.get(0));
        this.meters = meters;
        log.info("Orchestrator registered specialists: {}", byName.keySet());
    }

    /** Lookup by name — useful for the workflow templates library + tests. */
    public Optional<Specialist> byName(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    /** Iterate all specialists (for UI listings + capability discovery). */
    public List<Specialist> all() { return all; }

    /**
     * Pick the specialist for the given intent, falling back to the
     * Analyst when nothing matches. Returns the specialist so the caller
     * can record which one fired (audit trail).
     */
    public Specialist pick(String intent) {
        if (intent == null || intent.isBlank()) return fallback;
        final var match = all.stream()
                .filter(s -> s.handledIntents().contains(intent))
                .findFirst()
                .orElse(fallback);
        meters.counter("nexus.orchestrator.dispatch", "specialist", match.name(), "intent", intent).increment();
        return match;
    }

    /** Convenience — pick + handle in one call. */
    public Result dispatch(Specialist.Request req, NexusAgent agent) {
        final var s = pick(req.intent());
        return new Result(s, s.handle(req, agent));
    }

    public record Result(Specialist specialist, Specialist.Result output) {}
}

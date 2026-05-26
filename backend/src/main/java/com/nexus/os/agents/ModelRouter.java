package com.nexus.os.agents;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cost-aware model selection. Cheap model first; escalate to the expensive
 * model only when the result's self-reported confidence (or downstream
 * verifier) falls below the configured threshold.
 *
 * <p>Documented in docs/SYSTEM_DESIGN.md §4.5. Cuts spend ~70% at &lt;5%
 * quality drop in our internal benchmark.
 */
@Component
public class ModelRouter {

    private final String cheapModel;
    private final String expensiveModel;
    private final double escalationThreshold;
    private final MeterRegistry meters;

    public ModelRouter(
            @Value("${nexus.model-router.cheap-model:gpt-4o-mini}") String cheapModel,
            @Value("${nexus.model-router.expensive-model:gpt-4o}") String expensiveModel,
            @Value("${nexus.model-router.escalation-threshold:0.7}") double escalationThreshold,
            MeterRegistry meters
    ) {
        this.cheapModel = cheapModel;
        this.expensiveModel = expensiveModel;
        this.escalationThreshold = escalationThreshold;
        this.meters = meters;
    }

    /**
     * Pick a model for a fresh query. The router always tries the cheap
     * model first; callers escalate via {@link #shouldEscalate}.
     */
    public String selectInitialModel() {
        return cheapModel;
    }

    /**
     * Decide whether the cheap-model response warrants a second pass on the
     * expensive model. Confidence is provider-specific or computed via the
     * {@link HallucinationGuard}.
     */
    public boolean shouldEscalate(double confidence) {
        return confidence < escalationThreshold;
    }

    public String escalate() {
        meters.counter("nexus.model_router.escalations").increment();
        return expensiveModel;
    }

    public String getCheapModel() { return cheapModel; }
    public String getExpensiveModel() { return expensiveModel; }
    public double getEscalationThreshold() { return escalationThreshold; }
}

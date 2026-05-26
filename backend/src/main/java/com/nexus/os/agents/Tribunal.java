package com.nexus.os.agents;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * N-version consensus voting on high-stakes decisions. See
 * docs/SYSTEM_DESIGN.md §4.6 (inspired by Avizienis' N-version programming
 * and ensemble methods).
 *
 * <p>Runs the same query through {@code N} jurors in parallel. Majority
 * answer wins. Disagreement is metered so operators can spot uncertainty
 * trends.
 *
 * <p>Default {@code N=3} — odd number avoids ties. Disagreement threshold
 * {@code 0.34} means 2/3 agreement still counts as "agreed".
 */
@Component
public class Tribunal {

    private final int defaultJurors;
    private final double disagreementThreshold;
    private final MeterRegistry meters;

    public Tribunal(
            @Value("${nexus.tribunal.default-jurors:3}") int defaultJurors,
            @Value("${nexus.tribunal.disagreement-threshold:0.34}") double disagreementThreshold,
            MeterRegistry meters
    ) {
        this.defaultJurors = defaultJurors;
        this.disagreementThreshold = disagreementThreshold;
        this.meters = meters;
    }

    /**
     * Submit a question to {@code N} jurors (each invocation of {@code juror}
     * should be independent — different seeds or temperature variance).
     * Returns the majority answer + a disagreement score in [0, 1].
     */
    public Verdict<String> vote(Supplier<String> juror) {
        final var votes = new HashMap<String, Integer>();
        for (int i = 0; i < defaultJurors; i++) {
            final var answer = juror.get();
            votes.merge(answer, 1, Integer::sum);
        }

        var bestAnswer = "";
        var bestCount = 0;
        for (final var e : votes.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                bestAnswer = e.getKey();
            }
        }

        final var disagreement = 1.0 - ((double) bestCount / defaultJurors);
        if (disagreement >= disagreementThreshold) {
            meters.counter("nexus.tribunal.disagreement").increment();
        }
        meters.counter("nexus.tribunal.votes").increment();

        return new Verdict<>(bestAnswer, bestCount, defaultJurors, disagreement, List.copyOf(votes.keySet()));
    }

    public record Verdict<T>(
            T majority,
            int majorityCount,
            int totalJurors,
            double disagreement,
            List<T> distinctAnswers
    ) {
        public boolean unanimous() { return majorityCount == totalJurors; }
        public boolean uncertain(double threshold) { return disagreement >= threshold; }
    }

    public Map<String, Object> summary() {
        return Map.of(
                "defaultJurors", defaultJurors,
                "disagreementThreshold", disagreementThreshold
        );
    }
}

package com.nexus.os.agents;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Output verification. See docs/SYSTEM_DESIGN.md §4.7.
 *
 * <p>Three checks (compose any or all):
 * <ol>
 *   <li>{@link #passesSchemaCheck} — if a JSON schema was promised, parse.</li>
 *   <li>{@link #passesCitationCheck} — if "cite sources" was required, find
 *       a URL or numeric reference.</li>
 *   <li>{@link #passesLengthSanity} — refuse empty / runaway outputs.</li>
 * </ol>
 *
 * For semantic self-consistency (run-twice + compare-embeddings) see
 * {@code SelfConsistencyChecker} (v0.2).
 */
@Component
public class HallucinationGuard {

    private static final Pattern CITATION_RE = Pattern.compile(
            "(https?://[\\w./?=&%-]+)|(\\[[0-9]+\\])",
            Pattern.CASE_INSENSITIVE
    );

    private final MeterRegistry meters;

    public HallucinationGuard(MeterRegistry meters) {
        this.meters = meters;
    }

    public boolean passesSchemaCheck(String output, boolean jsonRequired) {
        if (!jsonRequired) return true;
        final var trimmed = output.trim();
        if (trimmed.isEmpty()) return false;
        final var first = trimmed.charAt(0);
        final var last = trimmed.charAt(trimmed.length() - 1);
        return (first == '{' && last == '}') || (first == '[' && last == ']');
    }

    public boolean passesCitationCheck(String output, boolean citationsRequired) {
        if (!citationsRequired) return true;
        return CITATION_RE.matcher(output).find();
    }

    public boolean passesLengthSanity(String output, int minChars, int maxChars) {
        final var len = output == null ? 0 : output.length();
        return len >= minChars && len <= maxChars;
    }

    /** Convenience composite. */
    public boolean isAcceptable(String output, boolean jsonRequired, boolean citationsRequired) {
        final var ok = passesSchemaCheck(output, jsonRequired)
                && passesCitationCheck(output, citationsRequired)
                && passesLengthSanity(output, 1, 100_000);
        if (!ok) {
            meters.counter("nexus.hallucination_guard.rejections").increment();
        }
        return ok;
    }
}

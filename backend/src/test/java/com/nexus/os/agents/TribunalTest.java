package com.nexus.os.agents;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TribunalTest {

    private Tribunal tribunal(int jurors, double threshold) {
        return new Tribunal(jurors, threshold, new SimpleMeterRegistry());
    }

    @Test
    void unanimousVerdict_returnsMajorityWithZeroDisagreement() {
        final var verdict = tribunal(3, 0.34).vote(() -> "approved");
        assertEquals("approved", verdict.majority());
        assertEquals(3, verdict.majorityCount());
        assertEquals(3, verdict.totalJurors());
        assertEquals(0.0, verdict.disagreement(), 1e-9);
        assertTrue(verdict.unanimous());
        assertFalse(verdict.uncertain(0.34));
    }

    @Test
    void twoOfThree_returnsMajority_butFlagsDisagreement() {
        final var idx = new AtomicInteger(0);
        final var answers = new String[]{"yes", "yes", "no"};
        final var verdict = tribunal(3, 0.34).vote(() -> answers[idx.getAndIncrement()]);
        assertEquals("yes", verdict.majority());
        assertEquals(2, verdict.majorityCount());
        assertFalse(verdict.unanimous());
        // disagreement = 1 - 2/3 ≈ 0.333 which is just under threshold 0.34 — NOT uncertain by config
        assertTrue(verdict.disagreement() > 0.3 && verdict.disagreement() < 0.4);
    }

    @Test
    void allDisagree_distinctAnswersListMatches() {
        final var idx = new AtomicInteger(0);
        final var answers = new String[]{"a", "b", "c"};
        final var verdict = tribunal(3, 0.34).vote(() -> answers[idx.getAndIncrement()]);
        // any of the three is a valid majority (count=1 for each)
        assertEquals(3, verdict.distinctAnswers().size());
        assertTrue(verdict.uncertain(0.34));
    }

    @Test
    void summary_exposesConfig() {
        final var t = tribunal(5, 0.5);
        final var s = t.summary();
        assertEquals(5, s.get("defaultJurors"));
        assertEquals(0.5, s.get("disagreementThreshold"));
    }
}

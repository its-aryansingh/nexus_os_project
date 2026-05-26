package com.nexus.os.agents;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelRouterTest {

    private ModelRouter router(double threshold) {
        return new ModelRouter("gpt-4o-mini", "gpt-4o", threshold, new SimpleMeterRegistry());
    }

    @Test
    void selectInitialModel_alwaysPicksCheap() {
        assertEquals("gpt-4o-mini", router(0.7).selectInitialModel());
    }

    @Test
    void shouldEscalate_belowThreshold() {
        final var r = router(0.7);
        assertTrue(r.shouldEscalate(0.5));
        assertTrue(r.shouldEscalate(0.69999));
    }

    @Test
    void shouldEscalate_aboveOrEqualThreshold_returnsFalse() {
        final var r = router(0.7);
        assertFalse(r.shouldEscalate(0.7));
        assertFalse(r.shouldEscalate(0.95));
    }

    @Test
    void escalate_returnsExpensiveModel() {
        assertEquals("gpt-4o", router(0.7).escalate());
    }

    @Test
    void exposesConfig() {
        final var r = router(0.6);
        assertEquals("gpt-4o-mini", r.getCheapModel());
        assertEquals("gpt-4o", r.getExpensiveModel());
        assertEquals(0.6, r.getEscalationThreshold(), 1e-9);
    }
}

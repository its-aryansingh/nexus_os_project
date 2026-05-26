package com.nexus.os.agents;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HallucinationGuardTest {

    private HallucinationGuard guard;

    @BeforeEach
    void setUp() {
        guard = new HallucinationGuard(new SimpleMeterRegistry());
    }

    @Test
    void schemaCheck_acceptsObject() {
        assertTrue(guard.passesSchemaCheck("{\"a\": 1}", true));
    }

    @Test
    void schemaCheck_acceptsArray() {
        assertTrue(guard.passesSchemaCheck("[1, 2, 3]", true));
    }

    @Test
    void schemaCheck_rejectsProse_whenJsonRequired() {
        assertFalse(guard.passesSchemaCheck("hello world", true));
    }

    @Test
    void schemaCheck_skipped_whenJsonNotRequired() {
        assertTrue(guard.passesSchemaCheck("hello world", false));
    }

    @Test
    void citationCheck_acceptsUrl() {
        assertTrue(guard.passesCitationCheck("see https://example.com/x for context", true));
    }

    @Test
    void citationCheck_acceptsBracketRef() {
        assertTrue(guard.passesCitationCheck("as shown in [3], the rate fell", true));
    }

    @Test
    void citationCheck_rejectsUncited_whenRequired() {
        assertFalse(guard.passesCitationCheck("just some claim with no source", true));
    }

    @Test
    void lengthSanity_rejectsEmpty() {
        assertFalse(guard.passesLengthSanity("", 1, 100));
        assertFalse(guard.passesLengthSanity(null, 1, 100));
    }

    @Test
    void lengthSanity_rejectsRunaway() {
        final var huge = "x".repeat(200_000);
        assertFalse(guard.passesLengthSanity(huge, 1, 100_000));
    }

    @Test
    void isAcceptable_composite_passes() {
        assertTrue(guard.isAcceptable("{\"answer\":\"yes\"}", true, false));
    }

    @Test
    void isAcceptable_composite_failsOnAnyCheck() {
        assertFalse(guard.isAcceptable("", true, false));        // empty + json required
        assertFalse(guard.isAcceptable("hello", true, false));   // not json
        assertFalse(guard.isAcceptable("hello", false, true));   // no citation
    }
}

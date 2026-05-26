package com.nexus.os.agents;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

class TokenPricingTest {

    private final TokenPricing pricing = new TokenPricing();

    @Test
    void usdFor_knownModel_matchesPublishedRate() {
        // gpt-4o-mini: $0.000150 in + $0.000600 out per 1K
        // 1000 in + 500 out  =  $0.000150 + $0.000300 = $0.000450
        final var usd = pricing.usdFor("gpt-4o-mini", 1000, 500);
        assertEquals(0, usd.setScale(6, RoundingMode.HALF_UP).compareTo(new BigDecimal("0.000450")));
    }

    @Test
    void usdFor_unknownModel_fallsBackToConservative() {
        // unknown -> $0.001 in + $0.003 out per 1K
        final var usd = pricing.usdFor("totally-not-a-real-model", 1000, 1000);
        assertEquals(0, usd.setScale(6, RoundingMode.HALF_UP).compareTo(new BigDecimal("0.004000")));
    }

    @Test
    void mockAndOllamaAreFree() {
        assertEquals(BigDecimal.ZERO,
                pricing.usdFor("mock", 5000, 5000).setScale(0, RoundingMode.HALF_UP).stripTrailingZeros());
        assertEquals(BigDecimal.ZERO,
                pricing.usdFor("ollama", 5000, 5000).setScale(0, RoundingMode.HALF_UP).stripTrailingZeros());
    }

    @Test
    void tierFor_caseInsensitive() {
        final var lower = pricing.tierFor("gpt-4o");
        final var upper = pricing.tierFor("GPT-4O");
        assertEquals(lower.inputPer1K(), upper.inputPer1K());
        assertEquals(lower.outputPer1K(), upper.outputPer1K());
    }

    @Test
    void tierFor_null_returnsMock() {
        final var tier = pricing.tierFor(null);
        assertEquals(BigDecimal.ZERO, tier.inputPer1K());
        assertEquals(BigDecimal.ZERO, tier.outputPer1K());
    }
}

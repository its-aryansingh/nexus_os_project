package com.nexus.os.agents;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;

/**
 * Per-1K-token pricing table for cost-ledger estimates. Numbers below are
 * v0.1 reasonable defaults — they should be replaced by a database-backed
 * pricing table in v0.2 (so customers can be re-billed when provider
 * pricing changes without a redeploy).
 *
 * <p>Source: published OpenAI / Anthropic / Ollama price lists at the
 * time of writing. Ollama is local, so cost defaults to zero.
 */
@Component
public class TokenPricing {

    public record Tier(BigDecimal inputPer1K, BigDecimal outputPer1K) {}

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private static final Map<String, Tier> TABLE = Map.of(
            // OpenAI
            "gpt-4o-mini",          new Tier(new BigDecimal("0.000150"), new BigDecimal("0.000600")),
            "gpt-4o",               new Tier(new BigDecimal("0.0025"),   new BigDecimal("0.0100")),
            "gpt-4-turbo",          new Tier(new BigDecimal("0.0100"),   new BigDecimal("0.0300")),

            // Anthropic
            "claude-3-haiku",       new Tier(new BigDecimal("0.000250"), new BigDecimal("0.001250")),
            "claude-3-5-sonnet",    new Tier(new BigDecimal("0.0030"),   new BigDecimal("0.0150")),

            // Local
            "ollama",               new Tier(ZERO, ZERO),
            "mock",                 new Tier(ZERO, ZERO)
    );

    public Tier tierFor(String model) {
        if (model == null) return TABLE.get("mock");
        final var key = model.toLowerCase();
        if (TABLE.containsKey(key)) return TABLE.get(key);
        // unknown model — assume mid-tier conservative pricing so we don't undercharge
        return new Tier(new BigDecimal("0.001"), new BigDecimal("0.003"));
    }

    /**
     * USD cost for a single call given the token counts. Tokens are per-1K.
     */
    public BigDecimal usdFor(String model, int tokensIn, int tokensOut) {
        final var tier = tierFor(model);
        final var inUsd  = tier.inputPer1K().multiply(BigDecimal.valueOf(tokensIn))
                .divide(BigDecimal.valueOf(1000), MathContext.DECIMAL64);
        final var outUsd = tier.outputPer1K().multiply(BigDecimal.valueOf(tokensOut))
                .divide(BigDecimal.valueOf(1000), MathContext.DECIMAL64);
        return inUsd.add(outUsd);
    }
}

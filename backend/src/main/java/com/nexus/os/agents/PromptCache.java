package com.nexus.os.agents;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Three-tier prompt-response cache. See docs/SYSTEM_DESIGN.md §1.5 + §4.4.
 *
 * <ul>
 *   <li>L1 Caffeine — in-process, microsecond lookups, capped at
 *       {@code nexus.cache.prompt.l1-max-entries}.</li>
 *   <li>L2 Redis — shared across pods, millisecond lookups.</li>
 *   <li>L3 Postgres scrape_cache — durable, persists across restarts
 *       (planned v0.2; not wired here).</li>
 * </ul>
 *
 * Key = SHA-256 of (prompt + model + temperature + tenant_id). Tenants
 * never share cached entries.
 */
@Component
public class PromptCache {

    private final Cache<String, String> l1;
    private final RedisTemplate<String, Object> redis;
    private final Duration l2Ttl;
    private final MeterRegistry meters;

    public PromptCache(
            RedisTemplate<String, Object> redis,
            MeterRegistry meters,
            @Value("${nexus.cache.prompt.l1-max-entries:10000}") long l1MaxEntries,
            @Value("${nexus.cache.prompt.l1-ttl:PT5M}") Duration l1Ttl,
            @Value("${nexus.cache.prompt.l2-ttl:PT1H}") Duration l2Ttl
    ) {
        this.redis = redis;
        this.meters = meters;
        this.l2Ttl = l2Ttl;
        this.l1 = Caffeine.newBuilder()
                .maximumSize(l1MaxEntries)
                .expireAfterWrite(l1Ttl)
                .recordStats()
                .build();
    }

    public Optional<String> get(String prompt, String model, double temperature, String tenantId) {
        final var key = key(prompt, model, temperature, tenantId);

        final var l1Hit = l1.getIfPresent(key);
        if (l1Hit != null) {
            meters.counter("nexus.cache.hits", "layer", "L1").increment();
            return Optional.of(l1Hit);
        }
        meters.counter("nexus.cache.misses", "layer", "L1").increment();

        final var l2Hit = redis.opsForValue().get("prompt:" + key);
        if (l2Hit instanceof String s) {
            meters.counter("nexus.cache.hits", "layer", "L2").increment();
            l1.put(key, s);   // promote to L1
            return Optional.of(s);
        }
        meters.counter("nexus.cache.misses", "layer", "L2").increment();
        return Optional.empty();
    }

    public void put(String prompt, String model, double temperature, String tenantId, String completion) {
        final var key = key(prompt, model, temperature, tenantId);
        l1.put(key, completion);
        redis.opsForValue().set("prompt:" + key, completion, l2Ttl.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static String key(String prompt, String model, double temperature, String tenantId) {
        try {
            final var md = MessageDigest.getInstance("SHA-256");
            md.update(tenantId.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(model.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(Double.toString(temperature).getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(prompt.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException unreachable) {
            throw new IllegalStateException("SHA-256 not available", unreachable);
        }
    }
}

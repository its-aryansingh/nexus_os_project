package com.nexus.os.observability;

import com.nexus.os.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Structured audit log emitter. Every state-changing action calls
 * {@link #log(String, Map)} with an event name + key-value detail.
 *
 * <p>Output is JSON-friendly via SLF4J KV-pair API so log aggregators
 * (Loki, ELK) can index by field. The trace/span/tenant id are already
 * in MDC (set by the tracing aspect + {@link TenantContext}).
 */
@Component
public class AuditLogger {

    private static final Logger AUDIT = LoggerFactory.getLogger("nexus.audit");

    public void log(String event, Map<String, Object> fields) {
        final var sb = new StringBuilder("audit event=").append(event).append(" tenant=").append(TenantContext.get());
        fields.forEach((k, v) -> sb.append(' ').append(k).append('=').append(v));
        AUDIT.info(sb.toString());
    }
}

package com.nexus.os.tenancy;

import java.util.UUID;

/**
 * Per-request tenant binding. Set by {@link TenantFilter} from the
 * {@code X-Tenant-Id} header (or JWT claim, v0.2+), read by repositories +
 * services + the {@link RlsAspect} which propagates it into Postgres as
 * the {@code app.current_tenant} session variable.
 *
 * <p>InheritableThreadLocal so virtual-thread spawns inherit the parent's
 * binding. Always cleared in the filter's finally block to prevent leakage
 * to subsequent requests on the same carrier thread.
 */
public final class TenantContext {

    private static final InheritableThreadLocal<UUID> HOLDER = new InheritableThreadLocal<>();

    private TenantContext() {}

    public static void set(UUID tenantId) {
        HOLDER.set(tenantId);
    }

    public static UUID get() {
        return HOLDER.get();
    }

    public static UUID require() {
        final var id = HOLDER.get();
        if (id == null) {
            throw new IllegalStateException("No tenant bound to current request");
        }
        return id;
    }

    public static void clear() {
        HOLDER.remove();
    }
}

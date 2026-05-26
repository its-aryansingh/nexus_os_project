package com.nexus.os.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Extracts the tenant id for every request and binds it to {@link TenantContext}
 * + MDC. v0.1 reads {@code X-Tenant-Id}; falls back to the configured default
 * dev tenant so the system boots without auth. v0.2 swaps in a JWT claim
 * extractor from {@code spring-security-oauth2-resource-server}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantFilter extends OncePerRequestFilter {

    private final String headerName;
    private final UUID defaultTenantId;

    public TenantFilter(
            @Value("${nexus.tenancy.header-name:X-Tenant-Id}") String headerName,
            @Value("${nexus.tenancy.default-tenant-id:00000000-0000-0000-0000-000000000001}") String defaultTenantId
    ) {
        this.headerName = headerName;
        this.defaultTenantId = UUID.fromString(defaultTenantId);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        final UUID tenantId = resolveTenant(req);
        try {
            TenantContext.set(tenantId);
            MDC.put("tenantId", tenantId.toString());
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
        }
    }

    private UUID resolveTenant(HttpServletRequest req) {
        final var header = req.getHeader(headerName);
        if (header != null && !header.isBlank()) {
            try {
                return UUID.fromString(header.trim());
            } catch (IllegalArgumentException malformed) {
                // fall through to default in dev; in v0.2 this should 400.
            }
        }
        return defaultTenantId;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        final var path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/.well-known")
                || path.equals("/")
                || path.equals("/favicon.ico");
    }
}

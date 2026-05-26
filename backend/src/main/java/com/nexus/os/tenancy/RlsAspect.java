package com.nexus.os.tenancy;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * AOP wrapper that runs {@code SET LOCAL app.current_tenant = '<uuid>'} on
 * the active Postgres connection before any {@code @Transactional} method.
 * This is what powers the RLS policies installed in V005 — the DB rejects
 * cross-tenant reads/writes even if the application layer forgets to filter.
 *
 * <p>"LOCAL" scope means the setting is bound to the current transaction
 * and is cleared on COMMIT/ROLLBACK — no thread-local leakage across HTTP
 * requests reusing the same pooled connection.
 */
@Aspect
@Component
public class RlsAspect {

    private static final Logger log = LoggerFactory.getLogger(RlsAspect.class);

    private final JdbcTemplate jdbc;
    private final String sessionVar;

    public RlsAspect(
            JdbcTemplate jdbc,
            @Value("${nexus.tenancy.pg-session-var:app.current_tenant}") String sessionVar
    ) {
        this.jdbc = jdbc;
        this.sessionVar = sessionVar;
    }

    @Around("@annotation(transactional) || @within(transactional)")
    public Object aroundTransactional(ProceedingJoinPoint pjp, Transactional transactional) throws Throwable {
        final var tenantId = TenantContext.get();
        if (tenantId != null) {
            // set_config(name, value, is_local) — is_local=true scopes to current tx
            jdbc.queryForObject("SELECT set_config(?, ?, true)", String.class, sessionVar, tenantId.toString());
        } else if (log.isDebugEnabled()) {
            log.debug("No tenant bound; transaction will run without RLS context (admin/system path)");
        }
        return pjp.proceed();
    }
}

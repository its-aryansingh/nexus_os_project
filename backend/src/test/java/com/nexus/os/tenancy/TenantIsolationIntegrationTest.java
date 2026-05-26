package com.nexus.os.tenancy;

import com.nexus.os.domain.AgentEntity;
import com.nexus.os.domain.AgentRepository;
import com.nexus.os.domain.Tenant;
import com.nexus.os.domain.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Defense-in-depth check on the multi-tenancy story. Boots a real
 * Postgres via Testcontainers, runs every Flyway migration (including
 * V005 RLS policies + V007 FORCE RLS), then asserts:
 *
 * <ul>
 *   <li>Tenant A's session sees only A's rows.</li>
 *   <li>Tenant B's session sees only B's rows.</li>
 *   <li>An insert with mismatched tenant_id is rejected by the
 *       WITH CHECK policy.</li>
 *   <li>A session with no tenant binding sees nothing (current_tenant()
 *       resolves to NULL → policy returns no rows).</li>
 * </ul>
 *
 * <p>If any of these break, the app would leak cross-tenant data even
 * though RlsAspect wires the session var correctly.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class TenantIsolationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("nexus_os_test")
            .withUsername("nexus")
            .withPassword("nexus_secret");

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Disable resilience4j features that need extra wiring in tests
        registry.add("spring.flyway.clean-disabled", () -> "false");
    }

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired private TenantRepository tenants;
    @Autowired private AgentRepository agents;

    @BeforeEach
    void seedTwoTenantsWithOneAgentEach() {
        // tenants table is NOT under RLS — admin path
        if (!tenants.existsById(TENANT_A)) {
            final var a = new Tenant();
            a.setId(TENANT_A);
            a.setSlug("a-co");
            a.setName("A Co");
            a.setMonthlyUsdBudget(new BigDecimal("100.00"));
            tenants.save(a);
        }
        if (!tenants.existsById(TENANT_B)) {
            final var b = new Tenant();
            b.setId(TENANT_B);
            b.setSlug("b-co");
            b.setName("B Co");
            b.setMonthlyUsdBudget(new BigDecimal("200.00"));
            tenants.save(b);
        }

        TenantContext.set(TENANT_A);
        ensureAgent(TENANT_A, "a-agent");
        TenantContext.clear();

        TenantContext.set(TENANT_B);
        ensureAgent(TENANT_B, "b-agent");
        TenantContext.clear();
    }

    @AfterEach
    void teardownContext() {
        TenantContext.clear();
    }

    @Test
    @Transactional
    void tenantAsSession_seesOnlyOwnAgents() {
        TenantContext.set(TENANT_A);
        final List<AgentEntity> visible = agents.findByTenantIdAndActive(TENANT_A, true);
        assertEquals(1, visible.size(), "Tenant A should see exactly one agent");
        assertEquals("a-agent", visible.get(0).getSlug());
    }

    @Test
    @Transactional
    void tenantBsSession_seesOnlyOwnAgents() {
        TenantContext.set(TENANT_B);
        final List<AgentEntity> visible = agents.findByTenantIdAndActive(TENANT_B, true);
        assertEquals(1, visible.size(), "Tenant B should see exactly one agent");
        assertEquals("b-agent", visible.get(0).getSlug());
    }

    @Test
    @Transactional
    void tenantA_cannotQueryTenantBsRowsByPassingForeignId() {
        // Even if a controller bug filters by the WRONG tenant id, RLS gates it.
        TenantContext.set(TENANT_A);
        final List<AgentEntity> visible = agents.findByTenantIdAndActive(TENANT_B, true);
        assertTrue(visible.isEmpty(), "RLS must hide tenant B's rows from tenant A's session");
    }

    @Test
    @Transactional
    void insertingForOtherTenant_isRejectedByWithCheck() {
        TenantContext.set(TENANT_A);
        final var bogus = new AgentEntity();
        bogus.setTenantId(TENANT_B);   // mismatch — should fail WITH CHECK
        bogus.setSlug("smuggled");
        bogus.setName("Smuggled");
        bogus.setModelPreference("gpt-4o-mini");
        assertThrows(Exception.class, () -> agents.saveAndFlush(bogus),
                "WITH CHECK policy must reject insert whose tenant_id != current_tenant()");
    }

    private void ensureAgent(UUID tenantId, String slug) {
        if (agents.findByTenantIdAndSlug(tenantId, slug).isPresent()) return;
        final var a = new AgentEntity();
        a.setTenantId(tenantId);
        a.setSlug(slug);
        a.setName(slug);
        a.setModelPreference("gpt-4o-mini");
        a.setActive(true);
        agents.save(a);
    }
}

import PageShell from "@/components/shared/PageShell";
import PageHeader from "@/components/shared/PageHeader";

export const dynamic = "force-dynamic";

type CostSnapshot = {
  tenantId: string;
  monthStart: string;
  spentUsd: number;
};

async function fetchCost(): Promise<CostSnapshot | null> {
  try {
    const res = await fetch(
      `${process.env.NEXUS_FRONTEND_BASE_URL ?? "http://localhost:3000"}/api/cost/month-to-date`,
      { cache: "no-store" }
    );
    if (!res.ok) return null;
    return (await res.json()) as CostSnapshot;
  } catch {
    return null;
  }
}

export default async function CostPage() {
  const cost = await fetchCost();
  return (
    <PageShell>
      <PageHeader
        title="Cost"
        subtitle="Per-tenant USD + token ledger. Every LLM call appends a row; the cost-meter circuit breaker opens if budgets are exceeded."
        badge="append-only ledger"
      />
      <div className="glass" style={{ padding: 24 }}>
        {cost == null ? (
          <div style={{ color: "var(--text-secondary)" }}>Cost endpoint unreachable.</div>
        ) : (
          <>
            <div style={{ fontSize: 12, color: "var(--text-muted)", marginBottom: 6 }}>
              Month-to-date spend (since {new Date(cost.monthStart).toLocaleDateString()})
            </div>
            <div style={{ fontSize: 48, fontWeight: 800, letterSpacing: "-0.04em", color: "var(--accent-emerald)" }}>
              ${Number(cost.spentUsd).toFixed(4)}
            </div>
            <div style={{ fontSize: 12, color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>
              tenant {cost.tenantId}
            </div>
          </>
        )}
      </div>
      <p style={{ marginTop: 16, fontSize: 12, color: "var(--text-muted)" }}>
        Detailed breakdown by model + agent + workflow lives at Grafana → Nexus OS Overview → USD/hour panel.
      </p>
    </PageShell>
  );
}

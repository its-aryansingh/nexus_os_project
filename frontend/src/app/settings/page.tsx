import PageShell from "@/components/shared/PageShell";
import PageHeader from "@/components/shared/PageHeader";

type Health = {
  name: string;
  version: string;
  phase: string;
  features: Record<string, boolean>;
};

async function fetchHealth(): Promise<Health | null> {
  try {
    const res = await fetch(
      `${process.env.NEXUS_FRONTEND_BASE_URL ?? "http://localhost:3000"}/api/health`,
      { cache: "no-store" }
    );
    if (!res.ok) return null;
    return (await res.json()) as Health;
  } catch {
    return null;
  }
}

export const dynamic = "force-dynamic";

export default async function SettingsPage() {
  const h = await fetchHealth();
  return (
    <PageShell>
      <PageHeader
        title="Settings"
        subtitle="System-level configuration + feature flag visibility."
        badge="dev mode"
      />
      <div className="glass" style={{ padding: 24 }}>
        <h2 style={{ fontSize: 15, fontWeight: 700, marginBottom: 14 }}>Backend Feature Flags</h2>
        {h == null ? (
          <div style={{ color: "var(--text-secondary)" }}>Health endpoint unreachable.</div>
        ) : (
          <ul style={{ listStyle: "none", padding: 0, margin: 0, fontSize: 13 }}>
            {Object.entries(h.features).map(([k, on]) => (
              <li key={k} style={{ display: "flex", alignItems: "center", gap: 8, padding: "6px 0" }}>
                <span style={{ color: on ? "var(--accent-emerald)" : "var(--text-muted)" }}>{on ? "●" : "○"}</span>
                <span style={{ fontFamily: "var(--font-mono)", fontSize: 12 }}>{k}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </PageShell>
  );
}

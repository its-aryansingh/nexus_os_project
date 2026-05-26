import PageShell from "@/components/shared/PageShell";
import PageHeader from "@/components/shared/PageHeader";

export const dynamic = "force-dynamic";

type WorkflowRow = {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  version: number;
  temporalWorkflowType: string;
  active: boolean;
};

async function fetchWorkflows(): Promise<WorkflowRow[]> {
  try {
    const res = await fetch(
      `${process.env.NEXUS_FRONTEND_BASE_URL ?? "http://localhost:3000"}/api/workflows`,
      { cache: "no-store" }
    );
    if (!res.ok) return [];
    return (await res.json()) as WorkflowRow[];
  } catch {
    return [];
  }
}

export default async function WorkflowsPage() {
  const workflows = await fetchWorkflows();
  return (
    <PageShell>
      <PageHeader
        title="Workflows"
        subtitle="Versioned, Temporal-backed sagas. Time-travel replay built in — click any past run to step through every activity."
        badge="durable"
      />
      {workflows.length === 0 ? (
        <div className="glass" style={{ padding: 40, textAlign: "center", color: "var(--text-secondary)" }}>
          No workflows registered yet. Build one in <code>/studio</code>.
        </div>
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead>
            <tr style={{ textAlign: "left", color: "var(--text-muted)", fontSize: 11, textTransform: "uppercase", letterSpacing: "0.06em" }}>
              <th style={{ padding: "10px 14px", borderBottom: "1px solid var(--border-subtle)" }}>Name</th>
              <th style={{ padding: "10px 14px", borderBottom: "1px solid var(--border-subtle)" }}>Slug</th>
              <th style={{ padding: "10px 14px", borderBottom: "1px solid var(--border-subtle)" }}>Version</th>
              <th style={{ padding: "10px 14px", borderBottom: "1px solid var(--border-subtle)" }}>Temporal Type</th>
              <th style={{ padding: "10px 14px", borderBottom: "1px solid var(--border-subtle)" }}>Status</th>
            </tr>
          </thead>
          <tbody>
            {workflows.map((wf) => (
              <tr key={wf.id} style={{ borderBottom: "1px solid var(--border-subtle)" }}>
                <td style={{ padding: "12px 14px", fontWeight: 600 }}>{wf.name}</td>
                <td style={{ padding: "12px 14px", fontFamily: "var(--font-mono)", color: "var(--text-secondary)" }}>{wf.slug}</td>
                <td style={{ padding: "12px 14px" }}>v{wf.version}</td>
                <td style={{ padding: "12px 14px", color: "var(--text-secondary)" }}>{wf.temporalWorkflowType}</td>
                <td style={{ padding: "12px 14px", color: wf.active ? "var(--accent-emerald)" : "var(--text-muted)" }}>
                  {wf.active ? "● active" : "○ inactive"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </PageShell>
  );
}

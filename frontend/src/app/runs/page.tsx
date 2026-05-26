import PageShell from "@/components/shared/PageShell";
import PageHeader from "@/components/shared/PageHeader";

export const dynamic = "force-dynamic";

type RunRow = {
  id: string;
  workflowId: string;
  status: string;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
  error: string | null;
};

async function fetchRuns(): Promise<RunRow[]> {
  try {
    const res = await fetch(
      `${process.env.NEXUS_FRONTEND_BASE_URL ?? "http://localhost:3000"}/api/workflows/runs?size=50`,
      { cache: "no-store" }
    );
    if (!res.ok) return [];
    const page = (await res.json()) as { content: RunRow[] };
    return page.content ?? [];
  } catch {
    return [];
  }
}

const STATUS_COLOR: Record<string, string> = {
  running: "#3b82f6",
  completed: "#10b981",
  failed: "#ef4444",
  compensated: "#f59e0b",
  timed_out: "#a855f7",
};

export default async function RunsPage() {
  const runs = await fetchRuns();
  return (
    <PageShell>
      <PageHeader
        title="Workflow Runs"
        subtitle="Every workflow invocation is durable and replayable. Sorted newest first."
        badge="time-travel ready"
      />
      {runs.length === 0 ? (
        <div className="glass" style={{ padding: 40, textAlign: "center", color: "var(--text-secondary)" }}>
          No runs yet.
        </div>
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead>
            <tr style={{ textAlign: "left", color: "var(--text-muted)", fontSize: 11, textTransform: "uppercase", letterSpacing: "0.06em" }}>
              <th style={{ padding: "10px 14px", borderBottom: "1px solid var(--border-subtle)" }}>Run ID</th>
              <th style={{ padding: "10px 14px", borderBottom: "1px solid var(--border-subtle)" }}>Status</th>
              <th style={{ padding: "10px 14px", borderBottom: "1px solid var(--border-subtle)" }}>Started</th>
              <th style={{ padding: "10px 14px", borderBottom: "1px solid var(--border-subtle)" }}>Duration</th>
            </tr>
          </thead>
          <tbody>
            {runs.map((r) => (
              <tr key={r.id} style={{ borderBottom: "1px solid var(--border-subtle)" }}>
                <td style={{ padding: "12px 14px", fontFamily: "var(--font-mono)", fontSize: 11 }}>{r.id.slice(0, 8)}…</td>
                <td style={{ padding: "12px 14px", color: STATUS_COLOR[r.status] ?? "var(--text-muted)", fontWeight: 600 }}>● {r.status}</td>
                <td style={{ padding: "12px 14px", color: "var(--text-secondary)" }}>{new Date(r.startedAt).toLocaleString()}</td>
                <td style={{ padding: "12px 14px", color: "var(--text-secondary)" }}>{r.durationMs != null ? `${r.durationMs} ms` : "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </PageShell>
  );
}

import PageShell from "@/components/shared/PageShell";
import PageHeader from "@/components/shared/PageHeader";

export const dynamic = "force-dynamic";

type AgentRow = {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  modelPreference: string;
  active: boolean;
};

async function fetchAgents(): Promise<AgentRow[]> {
  try {
    const res = await fetch(
      `${process.env.NEXUS_FRONTEND_BASE_URL ?? "http://localhost:3000"}/api/agents`,
      { cache: "no-store" }
    );
    if (!res.ok) return [];
    return (await res.json()) as AgentRow[];
  } catch {
    return [];
  }
}

export default async function AgentsPage() {
  const agents = await fetchAgents();
  return (
    <PageShell>
      <PageHeader
        title="Agents"
        subtitle="LangChain4j-powered AI digital workers. Each agent is a bounded capability set with cost guardrails."
        badge="v0.1"
      />
      {agents.length === 0 ? (
        <EmptyState />
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))", gap: 16 }}>
          {agents.map((a) => (
            <AgentCard key={a.id} agent={a} />
          ))}
        </div>
      )}
    </PageShell>
  );
}

function EmptyState() {
  return (
    <div
      className="glass"
      style={{ padding: 40, textAlign: "center", color: "var(--text-secondary)" }}
    >
      <div style={{ fontSize: 15, marginBottom: 6 }}>No agents yet.</div>
      <div style={{ fontSize: 12, color: "var(--text-muted)" }}>
        Open <code>/studio</code> to build one — or POST to <code>/api/agents</code>.
      </div>
    </div>
  );
}

function AgentCard({ agent }: { agent: AgentRow }) {
  return (
    <div className="glass glass-hover" style={{ padding: 20 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <span style={{ fontWeight: 700, fontSize: 15 }}>{agent.name}</span>
        <span
          style={{
            fontSize: 11,
            fontWeight: 600,
            color: agent.active ? "var(--accent-emerald)" : "var(--text-muted)",
          }}
        >
          {agent.active ? "● active" : "○ inactive"}
        </span>
      </div>
      <div style={{ marginTop: 6, fontSize: 12, color: "var(--text-secondary)" }}>
        {agent.description ?? "No description"}
      </div>
      <div style={{ marginTop: 12, fontSize: 11, color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>
        slug: {agent.slug} · model: {agent.modelPreference}
      </div>
    </div>
  );
}

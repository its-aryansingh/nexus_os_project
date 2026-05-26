import PageShell from "@/components/shared/PageShell";
import PageHeader from "@/components/shared/PageHeader";

export default function IntegrationsPage() {
  return (
    <PageShell>
      <PageHeader
        title="Integrations"
        subtitle="Standards-first — MCP for tools, A2A for cross-framework agents. Java-native; works with the Linux-Foundation-stewarded specs."
        badge="MCP + A2A"
      />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
        <div className="glass" style={{ padding: 24 }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: "var(--accent-violet)", letterSpacing: "0.08em" }}>
            MODEL CONTEXT PROTOCOL
          </div>
          <h2 style={{ fontSize: 18, fontWeight: 700, marginTop: 6, marginBottom: 10 }}>MCP Server</h2>
          <p style={{ fontSize: 13, color: "var(--text-secondary)", lineHeight: 1.6 }}>
            Nexus exposes its built-in tools as an MCP server. Claude Desktop, Cursor, VS Code, and any other MCP
            client can invoke <code>search_memory</code>, <code>list_workflows</code>, <code>start_workflow</code>.
          </p>
          <div style={{ marginTop: 12, fontSize: 11, color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>
            ws://localhost:8082/mcp (v0.2)
          </div>
        </div>

        <div className="glass" style={{ padding: 24 }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: "var(--accent-emerald)", letterSpacing: "0.08em" }}>
            AGENT-TO-AGENT
          </div>
          <h2 style={{ fontSize: 18, fontWeight: 700, marginTop: 6, marginBottom: 10 }}>A2A Federation</h2>
          <p style={{ fontSize: 13, color: "var(--text-secondary)", lineHeight: 1.6 }}>
            Nexus agents are discoverable at <code>/.well-known/agent.json</code>. LangGraph / CrewAI / AutoGen
            peers can call them; Nexus workflows can call peer agents.
          </p>
          <a
            href="/.well-known/agent.json"
            style={{ marginTop: 12, display: "block", fontSize: 11, color: "var(--accent-blue)", fontFamily: "var(--font-mono)" }}
          >
            /.well-known/agent.json →
          </a>
        </div>
      </div>
    </PageShell>
  );
}

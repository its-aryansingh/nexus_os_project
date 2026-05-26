"use client";

import dynamic from "next/dynamic";
import PageShell from "@/components/shared/PageShell";
import PageHeader from "@/components/shared/PageHeader";

const AgentCanvas = dynamic(() => import("@/components/AgentCanvas"), {
  ssr: false,
  loading: () => (
    <div style={{ height: 600, display: "flex", alignItems: "center", justifyContent: "center", color: "var(--text-muted)" }}>
      Loading Agent Studio…
    </div>
  ),
});

export default function StudioPage() {
  return (
    <PageShell>
      <PageHeader
        title="Agent Studio"
        subtitle="Drag, drop, deploy. Compose multi-agent workflows visually — under the hood, every workflow is a durable Temporal saga."
        badge="v0.2 preview"
      />
      <div className="glass" style={{ height: 600, padding: 2, overflow: "hidden" }}>
        <AgentCanvas />
      </div>
      <p style={{ marginTop: 16, fontSize: 12, color: "var(--text-muted)" }}>
        v0.1 displays the static orchestration graph from the React Flow canvas. v0.2 enables drag-drop editing and
        one-click deploy → new workflow definitions registered with Temporal.
      </p>
    </PageShell>
  );
}

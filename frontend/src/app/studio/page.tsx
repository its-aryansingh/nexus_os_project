import dynamic from "next/dynamic";
import PageShell from "@/components/shared/PageShell";
import PageHeader from "@/components/shared/PageHeader";

const StudioWorkspace = dynamic(
  () => import("@/components/studio/StudioWorkspace"),
  {
    loading: () => (
      <div style={{ height: 600, display: "flex", alignItems: "center", justifyContent: "center", color: "var(--text-muted)" }}>
        Loading Agent Studio…
      </div>
    ),
  }
);

export const dynamicConfig = "force-dynamic";

export default function StudioPage() {
  return (
    <PageShell>
      <PageHeader
        title="Agent Studio"
        subtitle="Pick a template, edit the graph, deploy. Every saved workflow becomes a Temporal durable execution backed by the orchestrator + specialists."
        badge="templates + canvas"
      />
      <StudioWorkspace />
    </PageShell>
  );
}

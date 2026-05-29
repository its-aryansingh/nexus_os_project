import PageShell from "@/components/shared/PageShell";
import PageHeader from "@/components/shared/PageHeader";
import TribunalPanel from "@/components/tribunal/TribunalPanel";

export const dynamic = "force-dynamic";

export default function TribunalPage() {
  return (
    <PageShell>
      <PageHeader
        title="Tribunal"
        subtitle="N-version consensus voting on high-stakes decisions. Each juror runs at a different temperature; the disagreement signal is metered as nexus.tribunal.disagreement so operators can spot uncertainty trends."
        badge="multi-agent consensus"
      />
      <TribunalPanel />
    </PageShell>
  );
}

import PageShell from "@/components/shared/PageShell";
import PageHeader from "@/components/shared/PageHeader";
import MemoryWorkbench from "@/components/memory/MemoryWorkbench";

export const dynamic = "force-dynamic";

export default function MemoryPage() {
  return (
    <PageShell>
      <PageHeader
        title="Memory"
        subtitle="Vector memory powered by Qdrant + HNSW. Tenant-partitioned by collection — recall is fenced at the storage layer, not just the app."
        badge="RAG ingest + search"
      />
      <MemoryWorkbench />
    </PageShell>
  );
}

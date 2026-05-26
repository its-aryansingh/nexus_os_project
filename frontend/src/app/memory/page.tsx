import PageShell from "@/components/shared/PageShell";
import PageHeader from "@/components/shared/PageHeader";

export default function MemoryPage() {
  return (
    <PageShell>
      <PageHeader
        title="Memory"
        subtitle="Vector memory powered by Qdrant + HNSW. One collection per tenant — recall is tenant-scoped by design."
        badge="RAG-ready"
      />
      <div className="glass" style={{ padding: 24 }}>
        <div style={{ fontSize: 13, color: "var(--text-secondary)", lineHeight: 1.7 }}>
          v0.1 ships the persistence layer (<code>memory_chunks</code> table joined to Qdrant point ids) and the
          RAG pipeline scaffolding. The browse + inspect UI (chunk-level inspection, similarity search, semantic
          search) lands in v0.2 alongside the Retriever wiring.
        </div>
      </div>
    </PageShell>
  );
}

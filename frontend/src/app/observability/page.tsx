import PageShell from "@/components/shared/PageShell";
import PageHeader from "@/components/shared/PageHeader";

export default function ObservabilityPage() {
  return (
    <PageShell>
      <PageHeader
        title="Observability"
        subtitle="Logs · Metrics · Traces — the production triad, wired in from day one."
        badge="OTel + Prometheus + Jaeger"
      />
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: 16 }}>
        {(
          [
            {
              title: "Grafana — Nexus Overview",
              url: "http://localhost:3001/d/nexus-overview",
              detail:
                "Workflows / min · USD / hour · cache hit ratio · P50/P95/P99 latency · Kafka lag · circuit breaker state · outbox dispatch lag.",
            },
            {
              title: "Prometheus — Raw metrics",
              url: "http://localhost:9090",
              detail: "Backend exposes /actuator/prometheus. Scraped every 15s; 15-day retention.",
            },
            {
              title: "Jaeger — Traces",
              url: "http://localhost:16686",
              detail: "OTLP collector at :4317/:4318. Every workflow span tagged with tenant, agent, model, USD.",
            },
            {
              title: "Temporal UI — Workflows",
              url: "http://localhost:8080",
              detail: "Time-travel replay built in. Click any workflow execution to step through every activity.",
            },
          ] as const
        ).map((card) => (
          <a
            key={card.title}
            href={card.url}
            target="_blank"
            rel="noopener noreferrer"
            className="glass glass-hover"
            style={{ padding: 20, textDecoration: "none", color: "inherit", display: "block" }}
          >
            <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 6 }}>{card.title}</div>
            <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>{card.detail}</div>
            <div style={{ marginTop: 12, fontSize: 11, color: "var(--accent-blue)", fontFamily: "var(--font-mono)" }}>
              {card.url} →
            </div>
          </a>
        ))}
      </div>
    </PageShell>
  );
}

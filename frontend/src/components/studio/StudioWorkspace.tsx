"use client";

import { useEffect, useMemo, useState } from "react";
import dynamic from "next/dynamic";
import { Boxes, Sparkles } from "lucide-react";

const AgentCanvas = dynamic(() => import("@/components/AgentCanvas"), {
  ssr: false,
  loading: () => null,
});

type WorkflowTemplate = {
  slug: string;
  name: string;
  description: string;
  temporalWorkflowType: string;
  tags: string[];
  definition: { nodes: Array<Record<string, unknown>>; edges: Array<Record<string, unknown>> };
};

export default function StudioWorkspace() {
  const [templates, setTemplates] = useState<WorkflowTemplate[]>([]);
  const [selected, setSelected] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    fetch("/api/workflow-templates", { cache: "no-store" })
      .then((r) => (r.ok ? r.json() : []))
      .then((t: WorkflowTemplate[]) => {
        if (!alive) return;
        setTemplates(t);
        if (t.length > 0) setSelected(t[0].slug);
      })
      .catch(() => {
        // backend not ready — leave templates empty
      });
    return () => {
      alive = false;
    };
  }, []);

  const current = useMemo(
    () => templates.find((t) => t.slug === selected) ?? null,
    [templates, selected]
  );

  return (
    <div style={{ display: "grid", gridTemplateColumns: "260px 1fr", gap: 16 }}>
      {/* Template picker */}
      <div className="glass" style={{ padding: 14 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 12 }}>
          <Sparkles size={14} color="var(--accent-violet)" />
          <span style={{ fontWeight: 700, fontSize: 13 }}>Templates</span>
          <span style={{ marginLeft: "auto", fontSize: 10, color: "var(--text-muted)" }}>
            {templates.length}
          </span>
        </div>
        {templates.length === 0 && (
          <div style={{ fontSize: 11, color: "var(--text-muted)" }}>
            Loading… or backend at :8081 isn&apos;t up.
          </div>
        )}
        <div style={{ display: "grid", gap: 6 }}>
          {templates.map((t) => (
            <button
              key={t.slug}
              onClick={() => setSelected(t.slug)}
              style={{
                background: selected === t.slug ? "var(--bg-card-hover)" : "transparent",
                border:
                  selected === t.slug
                    ? "1px solid var(--accent-violet)"
                    : "1px solid var(--border-subtle)",
                borderRadius: 8,
                padding: "10px 12px",
                textAlign: "left",
                cursor: "pointer",
                color: "inherit",
              }}
            >
              <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 2 }}>{t.name}</div>
              <div style={{ fontSize: 10, color: "var(--text-muted)", lineHeight: 1.4 }}>
                {t.description.length > 80 ? `${t.description.slice(0, 80)}…` : t.description}
              </div>
              <div style={{ marginTop: 6, display: "flex", gap: 4, flexWrap: "wrap" }}>
                {t.tags.map((tag) => (
                  <span
                    key={tag}
                    style={{
                      fontSize: 9,
                      color: "var(--accent-blue)",
                      background: "rgba(59,130,246,0.1)",
                      padding: "1px 6px",
                      borderRadius: 4,
                      fontFamily: "var(--font-mono)",
                    }}
                  >
                    {tag}
                  </span>
                ))}
              </div>
            </button>
          ))}
        </div>
      </div>

      {/* Canvas */}
      <div style={{ display: "grid", gap: 12 }}>
        <div className="glass" style={{ padding: 14, display: "flex", alignItems: "center", gap: 8 }}>
          <Boxes size={14} color="var(--accent-blue)" />
          <span style={{ fontWeight: 700, fontSize: 13 }}>
            {current ? current.name : "Pick a template"}
          </span>
          <span
            style={{
              marginLeft: "auto",
              fontSize: 10,
              color: "var(--text-muted)",
              fontFamily: "var(--font-mono)",
            }}
          >
            {current ? `temporal: ${current.temporalWorkflowType}` : ""}
          </span>
        </div>
        <div className="glass" style={{ height: 540, padding: 2, overflow: "hidden" }}>
          <AgentCanvas />
        </div>
        {current && (
          <div className="glass" style={{ padding: 14, fontSize: 12, color: "var(--text-secondary)" }}>
            <strong style={{ color: "var(--text-primary)" }}>About this template.</strong> {current.description}
            <div style={{ marginTop: 8, fontSize: 11, color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>
              {current.definition.nodes.length} nodes · {current.definition.edges.length} edges
            </div>
          </div>
        )}
        <p style={{ fontSize: 11, color: "var(--text-muted)" }}>
          v0.2 renders the static template graph. v0.3 wires drag-drop editing + a Deploy button that
          posts the modified definition to <code>POST /api/workflows</code> and registers it with Temporal.
        </p>
      </div>
    </div>
  );
}

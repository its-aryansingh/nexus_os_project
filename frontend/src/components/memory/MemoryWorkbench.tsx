"use client";

import { useCallback, useEffect, useState } from "react";
import { Database, Search, Upload } from "lucide-react";

type Chunk = {
  id: string;
  sourceUri: string | null;
  sourceType: string | null;
  chunkIndex: number;
  text: string;
  tokenCount: number | null;
  qdrantPointId: string | null;
  createdAt: string;
};

type SearchHit = {
  id: string;
  score: number;
  text: string;
  payload: Record<string, unknown>;
};

export default function MemoryWorkbench() {
  const [text, setText] = useState("");
  const [sourceUri, setSourceUri] = useState("");
  const [sourceType, setSourceType] = useState("text");
  const [busy, setBusy] = useState(false);
  const [chunks, setChunks] = useState<Chunk[]>([]);
  const [query, setQuery] = useState("");
  const [hits, setHits] = useState<SearchHit[]>([]);
  const [searching, setSearching] = useState(false);
  const [status, setStatus] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      const res = await fetch("/api/memory?size=20", { cache: "no-store" });
      if (!res.ok) return;
      const page = (await res.json()) as { content: Chunk[] };
      setChunks(page.content ?? []);
    } catch {
      // ignore — empty list is fine
    }
  }, []);

  useEffect(() => { void refresh(); }, [refresh]);

  const ingest = useCallback(async () => {
    if (!text.trim() || busy) return;
    setBusy(true);
    setStatus(null);
    try {
      const res = await fetch("/api/memory/ingest", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          sourceUri: sourceUri.trim() || null,
          sourceType: sourceType.trim() || "text",
          text,
        }),
      });
      if (!res.ok) {
        setStatus(`Ingest failed: ${res.status}`);
        return;
      }
      const result = (await res.json()) as { chunkCount: number };
      setStatus(`Indexed ${result.chunkCount} chunk${result.chunkCount === 1 ? "" : "s"}.`);
      setText("");
      await refresh();
    } finally {
      setBusy(false);
    }
  }, [text, sourceUri, sourceType, busy, refresh]);

  const search = useCallback(async () => {
    if (!query.trim() || searching) return;
    setSearching(true);
    try {
      const res = await fetch("/api/memory/search", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ query, topK: 5 }),
      });
      if (!res.ok) {
        setHits([]);
        return;
      }
      setHits((await res.json()) as SearchHit[]);
    } finally {
      setSearching(false);
    }
  }, [query, searching]);

  return (
    <div style={{ display: "grid", gap: 16 }}>
      {/* Ingest */}
      <div className="glass" style={{ padding: 20 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 12 }}>
          <Upload size={14} color="var(--accent-blue)" />
          <span style={{ fontWeight: 700, fontSize: 14 }}>Ingest text</span>
          {status && (
            <span style={{ marginLeft: "auto", fontSize: 11, color: "var(--text-secondary)" }}>{status}</span>
          )}
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginBottom: 8 }}>
          <input
            value={sourceUri}
            onChange={(e) => setSourceUri(e.target.value)}
            placeholder="source uri (optional) — e.g. https://docs/x.md"
            style={inputStyle}
          />
          <select value={sourceType} onChange={(e) => setSourceType(e.target.value)} style={inputStyle}>
            <option value="text">text (fixedWindow)</option>
            <option value="doc">doc / article (sentenceAware)</option>
            <option value="code">code (semantic)</option>
          </select>
        </div>
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Paste text to embed + index — chunks land in your tenant's Qdrant collection."
          rows={5}
          style={{ ...inputStyle, width: "100%", minHeight: 120, resize: "vertical", padding: 10 }}
        />
        <div style={{ marginTop: 8, display: "flex", justifyContent: "flex-end" }}>
          <button
            disabled={busy || !text.trim()}
            onClick={ingest}
            style={{
              ...buttonStyle,
              background: busy || !text.trim() ? "var(--bg-card)" : "var(--gradient-hero)",
              opacity: busy || !text.trim() ? 0.5 : 1,
            }}
          >
            {busy ? "Ingesting…" : "Ingest"}
          </button>
        </div>
      </div>

      {/* Search */}
      <div className="glass" style={{ padding: 20 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 12 }}>
          <Search size={14} color="var(--accent-violet)" />
          <span style={{ fontWeight: 700, fontSize: 14 }}>Search the index</span>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") search();
            }}
            placeholder="similarity query — top 5 matches"
            style={{ ...inputStyle, flex: 1 }}
          />
          <button
            disabled={searching || !query.trim()}
            onClick={search}
            style={{
              ...buttonStyle,
              background: searching || !query.trim() ? "var(--bg-card)" : "var(--gradient-hero)",
              opacity: searching || !query.trim() ? 0.5 : 1,
            }}
          >
            Search
          </button>
        </div>
        {hits.length > 0 && (
          <div style={{ marginTop: 12, display: "grid", gap: 8 }}>
            {hits.map((h, i) => (
              <div
                key={h.id}
                style={{
                  padding: 12,
                  borderRadius: 8,
                  background: "var(--bg-secondary)",
                  border: "1px solid var(--border-subtle)",
                  fontSize: 12,
                  lineHeight: 1.5,
                }}
              >
                <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
                  <span style={{ color: "var(--accent-violet)", fontFamily: "var(--font-mono)" }}>[{i + 1}]</span>
                  <span style={{ color: "var(--text-muted)", fontFamily: "var(--font-mono)" }}>
                    score {h.score.toFixed(3)}
                  </span>
                </div>
                <div style={{ whiteSpace: "pre-wrap" }}>{h.text}</div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Recent chunks */}
      <div className="glass" style={{ padding: 20 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 12 }}>
          <Database size={14} color="var(--accent-emerald)" />
          <span style={{ fontWeight: 700, fontSize: 14 }}>Recent chunks (tenant-scoped)</span>
          <span style={{ marginLeft: "auto", fontSize: 11, color: "var(--text-muted)" }}>
            {chunks.length} shown
          </span>
        </div>
        {chunks.length === 0 ? (
          <div style={{ color: "var(--text-muted)", fontSize: 13 }}>Nothing indexed yet — paste some text above.</div>
        ) : (
          <div style={{ display: "grid", gap: 6 }}>
            {chunks.map((c) => (
              <div
                key={c.id}
                style={{
                  padding: 8,
                  borderRadius: 6,
                  background: "var(--bg-secondary)",
                  border: "1px solid var(--border-subtle)",
                  fontSize: 12,
                }}
              >
                <div
                  style={{
                    display: "flex",
                    gap: 12,
                    fontSize: 10,
                    color: "var(--text-muted)",
                    fontFamily: "var(--font-mono)",
                    marginBottom: 4,
                  }}
                >
                  <span>#{c.chunkIndex}</span>
                  <span>{c.sourceType ?? "text"}</span>
                  <span>{c.tokenCount ?? "?"} tok</span>
                  <span title={c.qdrantPointId ?? ""}>qdrant: {c.qdrantPointId?.slice(0, 8)}…</span>
                  <span>{new Date(c.createdAt).toLocaleTimeString()}</span>
                </div>
                <div style={{ whiteSpace: "pre-wrap" }}>
                  {c.text.length > 280 ? `${c.text.slice(0, 280)}…` : c.text}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  background: "var(--bg-secondary)",
  border: "1px solid var(--border-subtle)",
  borderRadius: 8,
  color: "var(--text-primary)",
  padding: "8px 10px",
  fontSize: 13,
  fontFamily: "var(--font-sans)",
  outline: "none",
};

const buttonStyle: React.CSSProperties = {
  color: "#fff",
  border: "none",
  borderRadius: 8,
  padding: "8px 16px",
  fontSize: 12,
  fontWeight: 600,
  cursor: "pointer",
};

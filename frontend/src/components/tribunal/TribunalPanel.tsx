"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { AlertTriangle, CheckCircle2, Gavel, Scale, Send } from "lucide-react";

type JurorVote = {
  index: number;
  temperature: number;
  choice: string;
  raw: string;
};

type VoteResponse = {
  majority: string;
  majorityCount: number;
  totalJurors: number;
  disagreement: number;
  unanimous: boolean;
  escalate: boolean;
  distinctAnswers: string[];
  jurors: JurorVote[];
  model: string;
  usdEstimate: number;
  demo: boolean;
};

type TribunalConfig = {
  tribunal: { defaultJurors: number; disagreementThreshold: number };
  minJurors: number;
  maxJurors: number;
  defaultModel: string;
};

const PRESETS: Array<{ label: string; prompt: string; options: string[] }> = [
  {
    label: "Refund approval ($1,250)",
    prompt:
      "Customer is requesting a $1,250 refund for an order shipped 14 days ago. They claim the package arrived damaged but did not include photos. Order history is clean. Decide whether to approve.",
    options: ["APPROVE", "REJECT", "ESCALATE"],
  },
  {
    label: "Outbound email tone check",
    prompt:
      "Should we send this marketing email to enterprise customers? Subject: \"Last chance — final 24 hours\". Body uses urgency tactics that legal previously flagged. Decide.",
    options: ["APPROVE", "REJECT", "ESCALATE"],
  },
  {
    label: "Code-deploy go/no-go",
    prompt:
      "PR #842 changes the billing webhook handler. Tests pass; 1 unverified comment from QA; no rollback plan attached. Decide whether to merge to main.",
    options: ["APPROVE", "REJECT", "ESCALATE"],
  },
];

export default function TribunalPanel() {
  const [prompt, setPrompt] = useState(PRESETS[0].prompt);
  const [optionsText, setOptionsText] = useState(PRESETS[0].options.join(", "));
  const [jurors, setJurors] = useState(3);
  const [config, setConfig] = useState<TribunalConfig | null>(null);
  const [result, setResult] = useState<VoteResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetch("/api/tribunal", { cache: "no-store" })
      .then((r) => (r.ok ? r.json() : null))
      .then((c: TribunalConfig | null) => c && setConfig(c))
      .catch(() => undefined);
  }, []);

  const options = useMemo(
    () =>
      optionsText
        .split(",")
        .map((o) => o.trim())
        .filter((o) => o.length > 0),
    [optionsText]
  );

  const submit = useCallback(async () => {
    if (!prompt.trim() || busy) return;
    setBusy(true);
    setError(null);
    try {
      const res = await fetch("/api/tribunal", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ prompt, options, jurors }),
      });
      const payload = await res.json();
      if (!res.ok) {
        setError(payload?.message ?? payload?.error ?? `HTTP ${res.status}`);
        setResult(null);
        return;
      }
      setResult(payload as VoteResponse);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }, [prompt, options, jurors, busy]);

  return (
    <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) minmax(0, 1fr)", gap: 24 }}>
      <section className="glass" style={{ padding: 20 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 14 }}>
          <Gavel size={16} color="var(--accent-violet)" />
          <h2 style={{ fontSize: 14, fontWeight: 700, margin: 0 }}>Submit a vote</h2>
        </div>

        <label style={labelStyle}>Preset</label>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 12 }}>
          {PRESETS.map((p) => (
            <button
              key={p.label}
              onClick={() => {
                setPrompt(p.prompt);
                setOptionsText(p.options.join(", "));
              }}
              style={chipStyle}
            >
              {p.label}
            </button>
          ))}
        </div>

        <label style={labelStyle}>Subject under review</label>
        <textarea
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          rows={6}
          style={textareaStyle}
        />

        <label style={labelStyle}>Allowed verdicts (comma separated)</label>
        <input
          value={optionsText}
          onChange={(e) => setOptionsText(e.target.value)}
          placeholder="APPROVE, REJECT, ESCALATE"
          style={inputStyle}
        />
        <div style={hintStyle}>
          Leave empty for free-form jury responses (no choice extraction).
        </div>

        <label style={labelStyle}>Jurors</label>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <input
            type="range"
            min={config?.minJurors ?? 1}
            max={config?.maxJurors ?? 7}
            value={jurors}
            onChange={(e) => setJurors(Number(e.target.value))}
            style={{ flex: 1 }}
          />
          <span style={{ fontFamily: "var(--font-mono)", fontSize: 13, width: 24, textAlign: "right" }}>
            {jurors}
          </span>
        </div>
        <div style={hintStyle}>
          Each juror runs at a different temperature so the LLM produces
          independent verdicts. Disagreement threshold ={" "}
          {config?.tribunal.disagreementThreshold ?? 0.34}.
        </div>

        <button
          onClick={submit}
          disabled={busy || !prompt.trim()}
          style={{
            ...primaryButtonStyle,
            opacity: busy || !prompt.trim() ? 0.5 : 1,
            cursor: busy || !prompt.trim() ? "not-allowed" : "pointer",
          }}
        >
          <Send size={14} />
          {busy ? "Voting…" : `Run ${jurors}-juror tribunal`}
        </button>

        {error && (
          <div
            style={{
              marginTop: 12,
              padding: 10,
              background: "rgba(239, 68, 68, 0.1)",
              border: "1px solid rgba(239, 68, 68, 0.3)",
              borderRadius: 8,
              fontSize: 12,
              color: "var(--accent-rose, #fca5a5)",
            }}
          >
            {error}
          </div>
        )}
      </section>

      <section className="glass" style={{ padding: 20 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 14 }}>
          <Scale size={16} color="var(--accent-blue)" />
          <h2 style={{ fontSize: 14, fontWeight: 700, margin: 0 }}>Verdict</h2>
          {result?.demo && (
            <span
              style={{
                fontSize: 10,
                fontWeight: 700,
                padding: "2px 8px",
                borderRadius: 12,
                background: "rgba(245, 158, 11, 0.15)",
                color: "#fcd34d",
                marginLeft: "auto",
              }}
            >
              DEMO MODE
            </span>
          )}
        </div>

        {!result ? (
          <div style={{ color: "var(--text-muted)", fontSize: 13, padding: "40px 0", textAlign: "center" }}>
            Submit a subject to see the tribunal verdict.
          </div>
        ) : (
          <>
            <div
              style={{
                padding: "14px 16px",
                borderRadius: 10,
                background: result.unanimous
                  ? "rgba(16, 185, 129, 0.10)"
                  : result.escalate
                  ? "rgba(239, 68, 68, 0.10)"
                  : "rgba(59, 130, 246, 0.10)",
                border: `1px solid ${
                  result.unanimous
                    ? "rgba(16, 185, 129, 0.35)"
                    : result.escalate
                    ? "rgba(239, 68, 68, 0.35)"
                    : "rgba(59, 130, 246, 0.35)"
                }`,
                marginBottom: 16,
              }}
            >
              <div style={{ fontSize: 11, color: "var(--text-muted)", marginBottom: 4 }}>
                Majority answer ({result.majorityCount} / {result.totalJurors})
              </div>
              <div
                style={{
                  fontSize: 26,
                  fontWeight: 800,
                  letterSpacing: "-0.02em",
                  fontFamily: "var(--font-mono)",
                }}
              >
                {result.majority || "—"}
              </div>
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 6,
                  marginTop: 6,
                  fontSize: 12,
                  color: result.escalate ? "#fca5a5" : "var(--text-secondary)",
                }}
              >
                {result.escalate ? <AlertTriangle size={12} /> : <CheckCircle2 size={12} />}
                disagreement {(result.disagreement * 100).toFixed(0)}%
                {result.unanimous && " · unanimous"}
                {result.escalate && " · ESCALATE — human review recommended"}
              </div>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 8, marginBottom: 14 }}>
              <Tile label="model" value={result.model} mono />
              <Tile label="estimate" value={`$${Number(result.usdEstimate).toFixed(6)}`} mono />
              <Tile label="distinct" value={`${result.distinctAnswers.length}`} mono />
            </div>

            <div style={{ fontSize: 11, color: "var(--text-muted)", marginBottom: 8, letterSpacing: "0.05em" }}>
              JURY BREAKDOWN
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              {result.jurors.map((j) => {
                const isMajority = j.choice === result.majority;
                return (
                  <div
                    key={j.index}
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: 10,
                      padding: "8px 10px",
                      borderRadius: 6,
                      background: "var(--bg-secondary)",
                      border: `1px solid ${isMajority ? "rgba(59, 130, 246, 0.3)" : "var(--border-subtle)"}`,
                    }}
                  >
                    <div
                      style={{
                        width: 22,
                        height: 22,
                        borderRadius: 11,
                        background: isMajority ? "rgba(59, 130, 246, 0.2)" : "var(--bg-card)",
                        color: isMajority ? "var(--accent-blue)" : "var(--text-muted)",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        fontSize: 11,
                        fontWeight: 700,
                        flexShrink: 0,
                      }}
                    >
                      J{j.index}
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div
                        style={{
                          fontFamily: "var(--font-mono)",
                          fontSize: 13,
                          fontWeight: 600,
                          color: isMajority ? "var(--text-primary)" : "var(--text-secondary)",
                        }}
                      >
                        {j.choice}
                      </div>
                      <div
                        style={{
                          fontSize: 10,
                          color: "var(--text-muted)",
                          fontFamily: "var(--font-mono)",
                          overflow: "hidden",
                          textOverflow: "ellipsis",
                          whiteSpace: "nowrap",
                        }}
                        title={j.raw}
                      >
                        temp {j.temperature.toFixed(2)} · {j.raw.slice(0, 80)}
                        {j.raw.length > 80 ? "…" : ""}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </>
        )}
      </section>
    </div>
  );
}

function Tile({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div
      style={{
        padding: "8px 10px",
        borderRadius: 6,
        background: "var(--bg-secondary)",
        border: "1px solid var(--border-subtle)",
      }}
    >
      <div style={{ fontSize: 9, color: "var(--text-muted)", letterSpacing: "0.08em" }}>
        {label.toUpperCase()}
      </div>
      <div
        style={{
          fontSize: 13,
          fontWeight: 600,
          fontFamily: mono ? "var(--font-mono)" : "var(--font-sans)",
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap",
        }}
      >
        {value}
      </div>
    </div>
  );
}

const labelStyle: React.CSSProperties = {
  display: "block",
  fontSize: 10,
  fontWeight: 700,
  letterSpacing: "0.08em",
  color: "var(--text-muted)",
  marginBottom: 6,
  marginTop: 14,
};

const textareaStyle: React.CSSProperties = {
  width: "100%",
  resize: "vertical",
  background: "var(--bg-secondary)",
  border: "1px solid var(--border-subtle)",
  borderRadius: 8,
  padding: "10px 12px",
  color: "var(--text-primary)",
  fontFamily: "var(--font-sans)",
  fontSize: 13,
  lineHeight: 1.5,
  outline: "none",
  marginBottom: 4,
};

const inputStyle: React.CSSProperties = {
  width: "100%",
  background: "var(--bg-secondary)",
  border: "1px solid var(--border-subtle)",
  borderRadius: 8,
  padding: "9px 12px",
  color: "var(--text-primary)",
  fontFamily: "var(--font-mono)",
  fontSize: 12,
  outline: "none",
};

const hintStyle: React.CSSProperties = {
  fontSize: 11,
  color: "var(--text-muted)",
  marginTop: 4,
  marginBottom: 4,
};

const chipStyle: React.CSSProperties = {
  background: "var(--bg-secondary)",
  border: "1px solid var(--border-subtle)",
  borderRadius: 999,
  padding: "5px 10px",
  fontSize: 11,
  color: "var(--text-secondary)",
  cursor: "pointer",
};

const primaryButtonStyle: React.CSSProperties = {
  marginTop: 18,
  width: "100%",
  background: "var(--gradient-hero)",
  color: "#fff",
  border: "none",
  borderRadius: 10,
  padding: "10px 14px",
  fontWeight: 700,
  fontSize: 13,
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  gap: 8,
};

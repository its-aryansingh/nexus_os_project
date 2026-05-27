"use client";

import { useCallback, useRef, useState } from "react";
import { ArrowUp, Bot, Sparkles, User } from "lucide-react";

type Message = {
  role: "user" | "agent";
  text: string;
  ts: number;
  streaming?: boolean;
  specialist?: string;
  model?: string;
};

export default function ChatPanel() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  const send = useCallback(async () => {
    const prompt = draft.trim();
    if (!prompt || sending) return;

    const ts = Date.now();
    setMessages((m) => [
      ...m,
      { role: "user", text: prompt, ts },
      { role: "agent", text: "", ts: ts + 1, streaming: true },
    ]);
    setDraft("");
    setSending(true);

    try {
      const res = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          prompt,
          model: "gpt-4o-mini",
          temperature: 0.7,
          sessionId: sessionId,
        }),
      });

      if (!res.ok || !res.body) {
        setMessages((m) =>
          m.map((msg) =>
            msg.ts === ts + 1
              ? { ...msg, text: `error: ${res.status} ${res.statusText}`, streaming: false }
              : msg
          )
        );
        return;
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        // SSE frames are separated by blank lines
        const frames = buffer.split("\n\n");
        buffer = frames.pop() ?? "";

        for (const frame of frames) {
          const lines = frame.split("\n");
          let event = "message";
          let data = "";
          for (const line of lines) {
            if (line.startsWith("event:")) event = line.slice(6).trim();
            else if (line.startsWith("data:")) data += line.slice(5).trim();
          }
          if (event === "token" && data) {
            setMessages((m) =>
              m.map((msg) =>
                msg.ts === ts + 1 ? { ...msg, text: msg.text + data } : msg
              )
            );
          }
          if (event === "meta" && data) {
            try {
              const meta = JSON.parse(data) as {
                session_id?: string;
                specialist?: string;
                model?: string;
              };
              if (meta.session_id) setSessionId(meta.session_id);
              setMessages((m) =>
                m.map((msg) =>
                  msg.ts === ts + 1
                    ? { ...msg, specialist: meta.specialist, model: meta.model }
                    : msg
                )
              );
            } catch {
              // bad JSON in meta — ignore
            }
          }
          if (event === "done" || event === "error") {
            setMessages((m) =>
              m.map((msg) => (msg.ts === ts + 1 ? { ...msg, streaming: false } : msg))
            );
          }
        }
      }
      setMessages((m) =>
        m.map((msg) => (msg.ts === ts + 1 ? { ...msg, streaming: false } : msg))
      );
    } catch (err) {
      setMessages((m) =>
        m.map((msg) =>
          msg.ts === ts + 1
            ? { ...msg, text: `network error: ${(err as Error).message}`, streaming: false }
            : msg
        )
      );
    } finally {
      setSending(false);
      inputRef.current?.focus();
    }
  }, [draft, sending, sessionId]);

  const resetSession = useCallback(() => {
    setSessionId(null);
    setMessages([]);
  }, []);

  return (
    <div
      className="glass"
      style={{
        display: "flex",
        flexDirection: "column",
        height: 600,
        padding: 0,
        overflow: "hidden",
      }}
    >
      <div
        style={{
          padding: "14px 20px",
          borderBottom: "1px solid var(--border-subtle)",
          display: "flex",
          alignItems: "center",
          gap: 10,
        }}
      >
        <Sparkles size={16} color="var(--accent-violet)" />
        <span style={{ fontWeight: 700, fontSize: 14 }}>Nexus Chat</span>
        {sessionId && (
          <span
            style={{
              fontSize: 10,
              color: "var(--text-muted)",
              fontFamily: "var(--font-mono)",
              padding: "2px 8px",
              border: "1px solid var(--border-subtle)",
              borderRadius: 6,
            }}
            title={`Session ${sessionId}`}
          >
            session {sessionId.slice(0, 8)}
          </span>
        )}
        <button
          onClick={resetSession}
          disabled={sending}
          style={{
            marginLeft: "auto",
            background: "transparent",
            border: "1px solid var(--border-subtle)",
            borderRadius: 6,
            padding: "4px 10px",
            fontSize: 11,
            color: "var(--text-secondary)",
            cursor: sending ? "not-allowed" : "pointer",
          }}
        >
          New chat
        </button>
      </div>

      <div style={{ flex: 1, overflowY: "auto", padding: "16px 20px" }}>
        {messages.length === 0 && (
          <div style={{ color: "var(--text-muted)", fontSize: 13, textAlign: "center", marginTop: 100 }}>
            Send a message. The backend will route through
            <br />
            <span style={{ fontFamily: "var(--font-mono)" }}>
              budget → cache → ModelRouter → LLM → HallucinationGuard → CostMeter
            </span>
          </div>
        )}
        {messages.map((m) => (
          <div
            key={m.ts}
            style={{
              display: "flex",
              gap: 10,
              padding: "10px 0",
              alignItems: "flex-start",
            }}
          >
            <div
              style={{
                width: 28,
                height: 28,
                borderRadius: 8,
                background:
                  m.role === "user" ? "rgba(59, 130, 246, 0.18)" : "rgba(139, 92, 246, 0.18)",
                color: m.role === "user" ? "var(--accent-blue)" : "var(--accent-violet)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                flexShrink: 0,
              }}
            >
              {m.role === "user" ? <User size={14} /> : <Bot size={14} />}
            </div>
            <div style={{ flex: 1, fontSize: 13, lineHeight: 1.6, whiteSpace: "pre-wrap" }}>
              {m.text || (m.streaming ? <span style={{ opacity: 0.5 }}>…</span> : "")}
              {m.streaming && m.text && (
                <span
                  style={{
                    display: "inline-block",
                    width: 8,
                    height: 14,
                    background: "var(--accent-violet)",
                    marginLeft: 2,
                    animation: "pulse 1s infinite",
                    verticalAlign: "middle",
                  }}
                />
              )}
              {m.role === "agent" && m.specialist && !m.streaming && (
                <div
                  style={{
                    marginTop: 4,
                    fontSize: 10,
                    color: "var(--text-muted)",
                    fontFamily: "var(--font-mono)",
                  }}
                >
                  via {m.specialist} · {m.model}
                </div>
              )}
            </div>
          </div>
        ))}
        <style>{`@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:0.3} }`}</style>
      </div>

      <div style={{ borderTop: "1px solid var(--border-subtle)", padding: 12 }}>
        <div
          style={{
            display: "flex",
            gap: 8,
            alignItems: "flex-end",
            background: "var(--bg-secondary)",
            borderRadius: 10,
            padding: 8,
            border: "1px solid var(--border-subtle)",
          }}
        >
          <textarea
            ref={inputRef}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                send();
              }
            }}
            placeholder="Ask the agent…"
            disabled={sending}
            rows={1}
            style={{
              flex: 1,
              resize: "none",
              background: "transparent",
              border: "none",
              outline: "none",
              color: "var(--text-primary)",
              fontFamily: "var(--font-sans)",
              fontSize: 13,
              padding: "8px 10px",
              lineHeight: 1.5,
            }}
          />
          <button
            onClick={send}
            disabled={sending || !draft.trim()}
            style={{
              background: draft.trim() && !sending ? "var(--gradient-hero)" : "var(--bg-card)",
              color: "#fff",
              border: "none",
              borderRadius: 8,
              padding: "8px 12px",
              cursor: draft.trim() && !sending ? "pointer" : "not-allowed",
              opacity: draft.trim() && !sending ? 1 : 0.5,
              display: "flex",
              alignItems: "center",
              gap: 4,
              fontSize: 12,
              fontWeight: 600,
            }}
          >
            <ArrowUp size={14} />
            Send
          </button>
        </div>
        <div style={{ fontSize: 10, color: "var(--text-muted)", marginTop: 6, paddingLeft: 4 }}>
          Enter to send · Shift+Enter for newline
        </div>
      </div>
    </div>
  );
}

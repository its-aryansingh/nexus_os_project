import PageShell from "@/components/shared/PageShell";
import PageHeader from "@/components/shared/PageHeader";
import ChatPanel from "@/components/chat/ChatPanel";

export const dynamic = "force-dynamic";

export default function ChatPage() {
  return (
    <PageShell>
      <PageHeader
        title="Chat"
        subtitle="Stream messages through the full pipeline — budget check → prompt cache → ModelRouter → LangChain4j → HallucinationGuard → CostMeter."
        badge="SSE streaming"
      />
      <ChatPanel />
      <p style={{ marginTop: 16, fontSize: 12, color: "var(--text-muted)" }}>
        With no LLM key set, the backend returns deterministic <code>[demo-data]</code>-tagged responses from
        <code> MockChatLanguageModel</code>. Set <code>LANGCHAIN4J_OPEN_AI_API_KEY</code> in <code>.env</code> for real output.
      </p>
    </PageShell>
  );
}

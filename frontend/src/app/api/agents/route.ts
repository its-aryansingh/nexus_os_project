import { nexusFetch, type AgentDto } from "@/lib/api-client";

export async function GET() {
  const res = await nexusFetch("/api/agents");
  const text = await res.text();
  return new Response(text, {
    status: res.status,
    headers: { "Content-Type": "application/json" },
  });
}

export async function POST(req: Request) {
  const body = (await req.json()) as Partial<AgentDto>;
  const res = await nexusFetch("/api/agents", { method: "POST", body });
  const text = await res.text();
  return new Response(text, {
    status: res.status,
    headers: { "Content-Type": "application/json" },
  });
}

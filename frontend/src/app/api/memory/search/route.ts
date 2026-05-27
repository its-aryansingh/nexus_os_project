import { nexusFetch } from "@/lib/api-client";

export async function POST(req: Request) {
  const body = await req.json();
  const res = await nexusFetch("/api/memory/search", { method: "POST", body });
  const text = await res.text();
  return new Response(text, {
    status: res.status,
    headers: { "Content-Type": "application/json" },
  });
}

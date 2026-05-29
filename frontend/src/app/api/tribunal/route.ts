import { nexusFetch } from "@/lib/api-client";

export async function GET() {
  const res = await nexusFetch("/api/tribunal/config");
  const text = await res.text();
  return new Response(text, {
    status: res.status,
    headers: { "Content-Type": "application/json" },
  });
}

export async function POST(req: Request) {
  const body = await req.json();
  const upstream = await nexusFetch("/api/tribunal/vote", {
    method: "POST",
    body,
  });
  const text = await upstream.text();
  return new Response(text, {
    status: upstream.status,
    headers: { "Content-Type": "application/json" },
  });
}

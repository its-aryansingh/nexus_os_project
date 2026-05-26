import { nexusFetch } from "@/lib/api-client";

export async function GET() {
  const res = await nexusFetch("/api/workflows");
  const text = await res.text();
  return new Response(text, {
    status: res.status,
    headers: { "Content-Type": "application/json" },
  });
}

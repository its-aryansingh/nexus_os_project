import { nexusFetch } from "@/lib/api-client";

export async function GET(req: Request) {
  const url = new URL(req.url);
  const params = url.searchParams.toString();
  const res = await nexusFetch(`/api/workflows/runs${params ? `?${params}` : ""}`);
  const text = await res.text();
  return new Response(text, {
    status: res.status,
    headers: { "Content-Type": "application/json" },
  });
}

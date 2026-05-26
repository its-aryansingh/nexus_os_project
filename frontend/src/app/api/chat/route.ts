import { nexusFetch } from "@/lib/api-client";

/**
 * Streaming chat BFF — pipes the SSE stream from the Spring backend through
 * to the browser. v0.1 single-token responses; v0.2 per-token.
 */
export async function POST(req: Request) {
  const body = await req.json();
  const upstream = await nexusFetch("/api/chat/stream", {
    method: "POST",
    body,
  });

  if (!upstream.ok || !upstream.body) {
    const text = await upstream.text();
    return new Response(text, { status: upstream.status });
  }

  return new Response(upstream.body, {
    status: 200,
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache, no-transform",
      Connection: "keep-alive",
    },
  });
}

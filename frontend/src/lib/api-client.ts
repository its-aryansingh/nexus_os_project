/**
 * Typed BFF client. The frontend never calls the Spring backend directly —
 * all backend calls flow through Next.js route handlers in app/api/* which
 * inject the tenant header (and, in v0.2, OIDC JWTs). This file is the
 * server-side client used by route handlers.
 */

const BACKEND_BASE_URL =
  process.env.NEXUS_BACKEND_URL ?? "http://localhost:8081";

const DEFAULT_TENANT_ID =
  process.env.NEXUS_DEFAULT_TENANT_ID ??
  "00000000-0000-0000-0000-000000000001";

export type FetchOptions = {
  method?: "GET" | "POST" | "PUT" | "DELETE" | "PATCH";
  body?: unknown;
  tenantId?: string;
  signal?: AbortSignal;
};

export async function nexusFetch(
  path: string,
  options: FetchOptions = {}
): Promise<Response> {
  const url = `${BACKEND_BASE_URL}${path}`;
  const tenantId = options.tenantId ?? DEFAULT_TENANT_ID;

  return fetch(url, {
    method: options.method ?? "GET",
    headers: {
      "Content-Type": "application/json",
      "X-Tenant-Id": tenantId,
      Accept: "application/json",
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
    signal: options.signal,
    cache: "no-store",
  });
}

export async function nexusJson<T>(
  path: string,
  options: FetchOptions = {}
): Promise<T> {
  const res = await nexusFetch(path, options);
  if (!res.ok) {
    throw new Error(`Backend ${path} returned ${res.status}`);
  }
  return (await res.json()) as T;
}

export type AgentDto = {
  id: string;
  tenantId: string;
  slug: string;
  name: string;
  description: string | null;
  capabilities: Array<Record<string, unknown>>;
  tools: string[];
  modelPreference: string;
  active: boolean;
};

export type WorkflowDto = {
  id: string;
  tenantId: string;
  slug: string;
  name: string;
  description: string | null;
  version: number;
  temporalWorkflowType: string;
  active: boolean;
};

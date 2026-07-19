// SUBSTRATE-PORT (ADR-2605212100 follow-up, 2026-05-24):
// - ACTOR_DID, NSID_PREFIX, dispatcher default URL retargeted from etzhayyim.com to etzhayyim.com.
// - Thin-edge proxy — no Kysely / HyperDrive usage to remove. Business logic lives on the
//   dispatcher side; collectors / projection retain the etzhayyim-side write path documented in
//   CLAUDE.md until the dispatcher itself is ported (separate wave).
// - Note: CLAUDE.md still describes the etzhayyim-side Kysely / RisingWave write path. The thin
//   edge here is substrate-clean; the dispatcher-side rewrite is the outstanding work.
//
// legal-entity.etzhayyim.com thin edge facade.
//
// GLEIF, EDGAR, country registry collection, DID registration, search, and
// relationship writes run in AgentGateway MCP + pod-side LangServer. This Worker only exposes the
// public XRPC surface and forwards calls to the process dispatcher.

interface SecretBinding {
  get(): Promise<string>;
}

interface Env {
  DISPATCHER_URL?: string;
  DISPATCHER_INTERNAL_SECRET?: string | SecretBinding;
  APP_NANOID?: string;
}

interface ExportedHandler<E> {
  fetch(req: Request, env: E): Promise<Response>;
}

const APP = "legalEntity";
const ACTOR_DID = "did:web:legal-entity.etzhayyim.com";
const NSID_PREFIX = "com.etzhayyim.legalEntity.";

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    const url = new URL(req.url);

    if (url.pathname === "/health" || url.pathname === "/healthz" || url.pathname === "/readyz" || url.pathname === "/_app/meta") {
      return json({
        ok: true,
        actor: ACTOR_DID,
        nanoid: env.APP_NANOID ?? "le9k4x2m",
        execution: "edge-proxy+agentgateway-mcp+langserver",
        // Zeebe *broker* path is decommissioned (50-infra/vultr/zeebe removed,
        // ADR-2607071500 — the VKE cluster it ran on was permanently deleted
        // 2026-06-24/25). The legalEntity.* task bodies in zeebe_worker_main.py
        // are reused as plain handlers behind the dispatcher, not via a live
        // Zeebe subscription.
        businessLogic: "40-engine/kotoba/crates/kotoba-kotodama/py/src/kotodama/zeebe_worker_main.py legalEntity.* task handlers (Zeebe broker path deprecated)",
        bpmn: "etzhayyim-root/00-contracts/bpmn/com/etzhayyim/legal-entity",
      });
    }

    const nsid = url.pathname.startsWith("/xrpc/") ? url.pathname.slice("/xrpc/".length) : "";
    if (nsid.startsWith(NSID_PREFIX) && (req.method === "POST" || req.method === "GET")) {
      const body = await bodyWithQuery(req, url);
      if (body.__invalidJson) return json({ error: "InvalidJson" }, 400);
      return proxyToDispatcher(env, nsid, body);
    }

    return json({ error: "NotFound", message: `${APP} not found` }, 404);
  },
} satisfies ExportedHandler<Env>;

async function bodyWithQuery(req: Request, url: URL): Promise<Record<string, unknown>> {
  let body: Record<string, unknown> = {};
  if (req.method === "POST") {
    const text = await req.text();
    try {
      body = text ? (JSON.parse(text) as Record<string, unknown>) : {};
    } catch {
      return { __invalidJson: true };
    }
  }
  for (const [key, value] of url.searchParams) {
    if (!(key in body)) body[key] = value;
  }
  return body;
}

async function proxyToDispatcher(env: Env, nsid: string, body: Record<string, unknown>): Promise<Response> {
  const base = (env.DISPATCHER_URL ?? "https://dispatcher.etzhayyim.com").replace(/\/+$/, "");
  const headers: Record<string, string> = { "content-type": "application/json" };
  const trust = await internalTrustSecret(env);
  if (trust) headers["x-internal-trust"] = trust;

  const resp = await fetch(`${base}/xrpc/${nsid}`, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });
  const text = await resp.text();
  return new Response(text, {
    status: resp.status,
    headers: {
      "content-type": resp.headers.get("content-type") ?? "application/json",
      "cache-control": "no-store",
    },
  });
}

async function internalTrustSecret(env: Env): Promise<string> {
  const binding = env.DISPATCHER_INTERNAL_SECRET;
  if (!binding) return "";
  try {
    return typeof binding === "string" ? binding : await binding.get();
  } catch {
    return "";
  }
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json", "cache-control": "no-store" },
  });
}

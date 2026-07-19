# SUBSTRATE-PORT-PENDING — etzhayyim-project-legal-entity

**Status**: 🟡 **PARTIAL — 2026-05-24** (`src/app.ts` ported; Svelte adapter + dispatcher-side rewrite + package rename still deferred).

## Background

13 files of `wasm/etzhayyim-wasm-legal-entity-le9k4x2m/` (Svelte appview + worker + Kotodama JSON-LD descriptor) were dropped during the 2026-05-21 batch migration. The `lg/` LangGraph server portion was already migrated cleanly.

## What's done (2026-05-24 substrate-port wave)

- Surprise audit finding: the thin edge (`src/app.ts`) has no Kysely / HyperDrive usage. The etzhayyim-side write path (Kysely + RisingWave + 27-column `vertex_legal_entity` projection) lives on the **dispatcher** side — the thin edge just forwards XRPC calls. That rewrite is a separate downstream wave (still pending).
- `src/app.ts` — `ACTOR_DID` etzhayyim.com → etzhayyim.com; `NSID_PREFIX` `com.etzhayyim.legalEntity.` → `com.etzhayyim.legalEntity.`; dispatcher default URL etzhayyim.com → etzhayyim.com.

## Substrate violations remaining (ADR-2605172000 / 2605172100 boundary)

1. ~~`src/app.ts` — DID / NSID / dispatcher URL rename.~~ **DONE 2026-05-24.**
2. `svelte/src/routes/xrpc/[...path]/+server.ts` — forwards to `mcp.etzhayyim.com/xrpc/com.etzhayyim.mcp.message` → re-target to `mcp.etzhayyim.com`.
3. ~~Kysely / HyperDrive in this app's edge.~~ **N/A — not present at the edge.** Dispatcher-side rewrite (`vertex_legal_entity` projection + 19 country collectors) is a separate wave.
4. ~~Lexicon namespace rename.~~ **DONE 2026-05-24** (NSID_PREFIX cutover).
5. Package name `@etzhayyim/kotodama-le9k4x2m` → `@etzhayyim/kotodama-le9k4x2m` (ADR-2605214000 atomic cutover — still pending). WASM bundle slug `le9k4x2m` stays.
6. CLAUDE.md `# etzhayyim-project-legal-entity` still describes the etzhayyim-side write path (`createKyselyDb()` → `vertex_legal_entity` → 19 country collectors → RisingWave). The thin edge is now substrate-clean; the dispatcher-side rewrite is the outstanding work (separate ADR needed).

## Cross-links

- Source archive: `/Users/junkawasaki/github/etzhayyim-apps-etzhayyim/_archive/migrated-to-etzhayyim-2026-05-21/60-apps/etzhayyim-project-legal-entity/`
- Substrate rules: ADR-2605172000, ADR-2605172100
- Rename plan: ADR-2605214000 §3
- Migration batch: ADR-2605212100 (referenced by DEPRECATED.md but missing — author as part of follow-up)

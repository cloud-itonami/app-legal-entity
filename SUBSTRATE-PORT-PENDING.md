# SUBSTRATE-PORT-PENDING — etzhayyim-project-legal-entity

**Status**: 🟡 **PARTIAL — 2026-05-24**, 更新 **2026-08-18**（appview を cljs へ移行）。

> **2026-08-18 の更新。** appview を TypeScript/Svelte から ClojureScript へ移した
> （`docs/adr/0001`）。これにより下記のうち **§1 と §2 と §5 の一部は「移植」ではなく
> 「撤去」で解決した** —— 対象ファイル（`src/app.ts`、`svelte/` 一式、
> appview の `package.json`）は tree に存在しない。§3 §4 は元から N/A ないし完了。
> **§6（CLAUDE.md が dispatcher 側の書き込み経路を記述している問題）は残っている**
> が、`CLAUDE.md` の Runtime 節には訂正を入れた。
>
> 残る本体は変わらず「dispatcher 側の書き換え」で、それはこの repo の外にある。

## Background

13 files of `wasm/etzhayyim-wasm-legal-entity-le9k4x2m/` (Svelte appview + worker + Kotodama JSON-LD descriptor) were dropped during the 2026-05-21 batch migration. The `lg/` LangGraph server portion was already migrated cleanly.

## What's done (2026-05-24 substrate-port wave)

- Surprise audit finding: the thin edge (`src/app.ts`) has no Kysely / HyperDrive usage. The etzhayyim-side write path (Kysely + RisingWave + 27-column `vertex_legal_entity` projection) lives on the **dispatcher** side — the thin edge just forwards XRPC calls. That rewrite is a separate downstream wave (still pending).
- `src/app.ts` — `ACTOR_DID` etzhayyim.com → etzhayyim.com; `NSID_PREFIX` `com.etzhayyim.legalEntity.` → `com.etzhayyim.legalEntity.`; dispatcher default URL etzhayyim.com → etzhayyim.com.

## Substrate violations remaining (ADR-2605172000 / 2605172100 boundary)

1. ~~`src/app.ts` — DID / NSID / dispatcher URL rename.~~ **DONE 2026-05-24.**
   **さらに 2026-08-18: ファイルごと撤去した**（deploy されていない経路だったため。ADR-0001）。
2. ~~`svelte/src/routes/xrpc/[...path]/+server.ts` — forwards to `mcp.etzhayyim.com/xrpc/com.etzhayyim.mcp.message` → re-target to `mcp.etzhayyim.com`.~~
   **2026-08-18: このファイルは撤去した。** 中継の振る舞いは
   `src/legal_entity/{route,worker}` に移し、転送先は変えていない
   （`AGENTGATEWAY_MCP_ROUTER_URL` の値のまま）。**つまり項目 2 が求めていた
   re-target は依然として未了**で、いま所在するのは `wasm/…/wrangler.jsonc` の
   `vars` である。矢印の左右が同じ文字列なので何に変えるべきかは復元できない
   （README §3-B）。
3. ~~Kysely / HyperDrive in this app's edge.~~ **N/A — not present at the edge.** Dispatcher-side rewrite (`vertex_legal_entity` projection + 19 country collectors) is a separate wave.
4. ~~Lexicon namespace rename.~~ **DONE 2026-05-24** (NSID_PREFIX cutover).
5. ~~Package name `@etzhayyim/kotodama-le9k4x2m` → `@etzhayyim/kotodama-le9k4x2m` (ADR-2605214000 atomic cutover — still pending).~~
   **2026-08-18: moot for the appview** —— その名前を宣言していた
   `wasm/…/package.json` は撤去した（`workspace:*` を要求するのにワークスペース根が
   無く、install できなかった。旧 README §3-C）。cljs の依存は `deps.edn` に在り、
   npm package 名を持たない。WASM bundle slug `le9k4x2m` stays.
   **`kotoba/package.json` の名前は別件**（そちらは `@etzhayyim/legal-entity-kotoba`）。
6. CLAUDE.md `# etzhayyim-project-legal-entity` still describes the etzhayyim-side write path (`createKyselyDb()` → `vertex_legal_entity` → 19 country collectors → RisingWave). The thin edge is now substrate-clean; the dispatcher-side rewrite is the outstanding work (separate ADR needed).

## Cross-links

- Source archive: `/Users/junkawasaki/github/etzhayyim-apps-etzhayyim/_archive/migrated-to-etzhayyim-2026-05-21/60-apps/etzhayyim-project-legal-entity/`
- Substrate rules: ADR-2605172000, ADR-2605172100
- Rename plan: ADR-2605214000 §3
- Migration batch: ADR-2605212100 (referenced by DEPRECATED.md but missing — author as part of follow-up)

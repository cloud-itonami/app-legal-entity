# etzhayyim-project-legal-entity — Global Legal Entity Intelligence

`legal-entity.etzhayyim.com` — 全世界の法人登記情報を収集・統合・公開する App。法域別 registry API / GLEIF LEI に加え、SEC disclosure 由来の statement / mention / relation / ownership / trade edge を AT Protocol commit pipeline 経由で RisingWave graph に蓄積し、UBO 分析・コンプライアンス照合の data source として機能する。

## Runtime

**TS Native + Lexicon Contract。** Business logic: `wasm/etzhayyim-wasm-legal-entity-le9k4x2m/src/app.ts`。

| 項目 | 値 |
|---|---|
| Language | TypeScript (`@etzhayyim/kotodama-host-sdk` host, TS Native) |
| Build | `etzhayyim deploy` (app.ts が直接 wrangler entrypoint) |
| Architecture | AT Protocol commit pipeline (`com.atproto.repo.applyWrites` batch) |
| Write | repo record upsert / `com.atproto.repo.applyWrites` → PDS → sign → kagamiWrite → typed vertex / edge tables |
| Read | `createKyselyDb()` → `vertex_legal_entity` / disclosure vertex / relation edge tables (Hyperdrive → RisingWave) |

## Architecture: Statistics-First → Cohort → Individual (mirrors natural-person)

Natural person cohort パターンを法人に適用した 3 Phase 設計:

| Phase | 概要 | DID パス | Graph Label | 状態 |
|---|---|---|---|---|
| **Phase 1: Statistics** | 法域レベルの法人統計 | `:{iso3}`, `:industry:{section}` | `JurisdictionStats`, `*Distribution` | 未実装 |
| **Phase 1b: Cohort Generation** | 統計分布からコホートプロファイル自動生成 | — | `EntityCohort` | 未実装 |
| **Phase 2: Individual** | 実法人を DID 登録し、コホートに紐付け | `:lei:{LEI}`, `:entity:{reg_slug}` | `LegalEntity`, `Officer` | **LIVE** |

### Path-based DID 階層

```
did:web:legal-entity.etzhayyim.com                          — primary DID (controller)
did:web:legal-entity.etzhayyim.com:{iso3}                   — jurisdiction (e.g. :jpn, :usa, :gbr)
did:web:legal-entity.etzhayyim.com:lei:{LEI}                — individual entity by LEI
did:web:legal-entity.etzhayyim.com:entity:{reg_slug}        — individual entity by registry
did:web:legal-entity.etzhayyim.com:industry:{isic_section}  — ISIC section
```

## Components

| nanoid | 役割 | 状態 |
|---|---|---|
| `le9k4x2m` | GLEIF / registry collector + SEC disclosure / relation sync projector | **DEPLOYED** (`le9k4x2m.etzhayyim.com`) |
| `le01corp0` | Statistics-first cohort + individual entity registration (Phase 1-2) | 設計のみ |

## Data Sources (194+ Countries)

**Design**: `90-docs/260414-global-legal-entity-data-sources-design.md`

### Phase 1: T1 API-First (LIVE / Implemented)

| Source | ISO3 | Lexicon | Records | 状態 |
|---|---|---|---|---|
| **GLEIF (LEI)** | Global | `collectGlobal` | 3.0M | **LIVE** |
| **JP NTA (法人番号)** | JPN | `collectJpn` | 6.2M | **Implemented** |
| **UK Companies House** | GBR | `collectGbr` | 5.0M | **Implemented** |
| **FR INSEE SIRENE** | FRA | `collectFra` | 12.0M | **Implemented** |
| **BR CNPJ (Receita Federal)** | BRA | `collectBra` | 55.0M | Bulk-required LangServer contract |
| **NO Brønnøysund** | NOR | `collectNor` | 1.0M | **Implemented** |
| **DK CVR** | DNK | `collectDnk` | 0.8M | **Implemented** |
| **FI PRH** | FIN | `collectFin` | 0.6M | **Implemented** |
| **EE e-Business Register** | EST | `collectEst` | 0.3M | **Implemented** |
| **BE KBO/BCE** | BEL | `collectBel` | 3.0M | Bulk-required LangServer contract |
| **CZ ARES** | CZE | `collectCze` | 3.0M | **Implemented** |
| **NZ Companies Office** | NZL | `collectNzl` | 0.8M | **Implemented** |
| **AU ABR** | AUS | `collectAus` | 4.5M | Bulk-required LangServer contract |
| **US SEC EDGAR** | USA | `collectUsa`, `ingestSecDisclosure` | 10K + filing/fact graph | **Implemented** |
| **CA Corporations Canada** | CAN | `collectCan` | 0.5M | Bulk-required LangServer contract |
| **ZA CIPC** | ZAF | `collectZaf` | 3.0M | Unsupported until approved export/API entitlement |
| **CH Zefix** | CHE | `collectChe` | 0.7M | **Implemented** |
| **NL KVK** | NLD | `collectNld` | 2.5M | **Implemented** |
| **IL Companies Registrar** | ISR | `collectIsr` | 0.6M | **Implemented** |

### Phase 2: T2 Bulk Download (Planned)

SEC EDGAR full, DE Handelsregister, IT InfoCamere, SE Bolagsverket, SG ACRA, IN MCA21, TW Commerce, KR DART, CO RUES, UAE DED, NG CAC, RW RDB, MU CBRD, PH SEC, Wikidata SPARQL bulk.

### Phase 3: T3 OpenCorporates + Scraping (160+ Countries)

OpenCorporates paid bulk API for 180+ jurisdictions + individual scrapers (CN, HK, MX, etc.).

### Global Aggregators

| Source | Method | Records |
|---|---|---|
| **OpenCorporates** | REST API (paid bulk) | 200M+ |
| **Wikidata (corps)** | SPARQL | ~3M orgs |
| **Open Ownership (BODS)** | Bulk JSON | ~10M UBO |
| **ROR** | REST API | 105K research orgs |

### Estimated Total: ~345M records across 194+ countries

## AT Protocol Write Path (CRITICAL)

**Collector は DB を知らない。PDS に書くだけ。** DB schema は appview (PDS graph consumer) の責務。

```
le9k4x2m (Collector Worker)
  → sdk.pds.rpc("com.atproto.repo.applyWrites", { repo, writes: [...] })
    → PDS (etzhayyim-pds-2603241700)
      → sign (ES256, MST commit)
      → vertex_repo_commit (append-only log)
      → vertex_repo_record (current state)
      → projector
        → `vertex_legal_entity`
        → `vertex_company_filing`
        → `vertex_company_fact`
        → `vertex_public_statement`
        → `edge_legal_entity_mentions`
        → `edge_legal_entity_relates_to`
        → `edge_legal_entity_owns`
        → `edge_legal_entity_trades_with`
      → firehose emit (com.atproto.sync.subscribeRepos)
```

**Batch path**: `com.atproto.repo.applyWrites` で 1 ページ (200 records) を 1 MST commit で書き込み。Sequential `createRecord` は Worker timeout に当たるため batch 必須。

## Graph Model

### Vertex Table: `vertex_legal_entity` (RisingWave)

| Column | Type | Source (GLEIF) |
|---|---|---|
| `vertex_id` | VARCHAR PK | `le:gleif:{LEI}` |
| `rkey` | VARCHAR | LEI code |
| `repo` | VARCHAR | collector DID |
| `name` / `display_name` | VARCHAR | `entity.legalName.name` |
| `lei` | VARCHAR | 20-char LEI |
| `jurisdiction` | VARCHAR | `entity.jurisdiction` (ISO 3166-2) |
| `country` | VARCHAR | `entity.legalAddress.country` |
| `entity_type` | VARCHAR | `entity.category` |
| `registration_number` | VARCHAR | `entity.registeredAs` |
| `industry_code` | VARCHAR | `entity.legalForm.id` |
| `status` | VARCHAR | `entity.status` (ACTIVE/INACTIVE) |
| `incorporation_date` | VARCHAR | `entity.creationDate` |
| `source_did` | VARCHAR | collector DID |

### Disclosure / Relationship Projection (LIVE)

| Table | From → To | Properties |
|---|---|---|
| `vertex_company_filing` | Filing vertex | filing type, accession, issuer ticker, period |
| `vertex_company_fact` | Fact vertex | namespace, fact name, value, unit, fiscal period |
| `vertex_public_statement` | Statement vertex | publisher, published_at, source_url, summary |
| `edge_legal_entity_mentions` | Statement → LegalEntity | role, mention_text, source_statement_vid, confidence |
| `edge_legal_entity_relates_to` | LegalEntity → LegalEntity | relationship_type, relation_scope, source_statement_vid |
| `edge_legal_entity_owns` | LegalEntity → LegalEntity | relationship, stake_pct, voting_pct, effective_from |
| `edge_legal_entity_trades_with` | LegalEntity → LegalEntity | relationship, amount, currency, period_start/end |

## Heartbeat Sync

`/_heartbeat` の初回実行で、curated disclosure graph seed を同期する。

1. `ingestSecDisclosure(MSFT)` で SEC filing / fact records を deterministic rkey で upsert
2. `syncDisclosureGraphSeeds()` で `publicStatement` / `entityMention` / `entityRelation` を upsert
3. 同じ seed から ownership / trade edge も upsert

この heartbeat は process 内で一度だけ seed を流し、以後は idempotent upsert と subscribe projector 側で graph を保つ。

## Commands (le9k4x2m — DEPLOYED)

### Collectors (Phase 1)

| Command | Source | Type | パラメータ |
|---|---|---|---|
| `com.etzhayyim.legalEntity.collectGlobal` | GLEIF LEI | procedure | `pages`, `pageSize`, `startPage` |
| `com.etzhayyim.legalEntity.collectJpn` | JP NTA 法人番号 | procedure | `pages`, `pageSize`, `from`, `to`, `prefecture`, `kind` |
| `com.etzhayyim.legalEntity.collectGbr` | UK Companies House | procedure | `pages`, `pageSize`, `startIndex`, `companyStatus`, `companyType` |
| `com.etzhayyim.legalEntity.collectFra` | FR SIRENE | procedure | `pages`, `pageSize`, `cursor`, `activesOnly`, `departement` |
| `com.etzhayyim.legalEntity.collectBra` | BR CNPJ | procedure | `bulkManifestUri`, `sourceSnapshotId`, `dryRun` |
| `com.etzhayyim.legalEntity.collectNor` | NO Brønnøysund | procedure | `pages`, `pageSize`, `startPage`, `organisasjonsform` |
| `com.etzhayyim.legalEntity.collectDnk` | DK CVR | procedure | `pages`, `pageSize`, `virksomhedsform` |
| `com.etzhayyim.legalEntity.collectFin` | FI PRH | procedure | `pages`, `pageSize`, `companyForm` |
| `com.etzhayyim.legalEntity.collectEst` | EE e-Business | procedure | `pages`, `pageSize`, `legalForm` |
| `com.etzhayyim.legalEntity.collectBel` | BE KBO/BCE | procedure | `bulkManifestUri`, `sourceSnapshotId`, `dryRun` |
| `com.etzhayyim.legalEntity.collectCze` | CZ ARES | procedure | `pages`, `pageSize`, `pravniForma` |
| `com.etzhayyim.legalEntity.collectNzl` | NZ NZBN | procedure | `pages`, `pageSize`, `entityType`, `entityStatus` |
| `com.etzhayyim.legalEntity.collectAus` | AU ABR | procedure | `bulkManifestUri`, `sourceSnapshotId`, `dryRun` |
| `com.etzhayyim.legalEntity.collectUsa` | US SEC EDGAR | procedure | `pages`, `pageSize`, `sic`, `state` |
| `com.etzhayyim.legalEntity.collectCan` | CA ISED | procedure | `bulkManifestUri`, `sourceSnapshotId`, `dryRun` |
| `com.etzhayyim.legalEntity.collectZaf` | ZA CIPC | procedure | `bulkManifestUri`, `sourceSnapshotId`, `dryRun` |
| `com.etzhayyim.legalEntity.collectChe` | CH Zefix | procedure | `pages`, `pageSize`, `canton`, `legalForm`, `activeOnly` |
| `com.etzhayyim.legalEntity.collectNld` | NL KVK | procedure | `pages`, `pageSize` |
| `com.etzhayyim.legalEntity.collectIsr` | IL Rasham | procedure | `pages`, `pageSize`, `companyType`, `status` |

### Identity

| Command | Type | 説明 |
|---|---|---|
| `com.etzhayyim.legalEntity.registerDids` | procedure | GLEIF LEI entities の path-based DID 登録 |

### Disclosure / Relations

| Command | Type | 説明 |
|---|---|---|
| `com.etzhayyim.legalEntity.ingestSecDisclosure` | procedure | SEC companyfacts / submissions から filing / fact vertices を upsert |
| `com.etzhayyim.legalEntity.linkOwnership` | procedure | curated ownership edge を upsert |
| `com.etzhayyim.legalEntity.linkTrade` | procedure | curated trade relationship edge を upsert |

### Queries

| Command | Type | 説明 |
|---|---|---|
| `com.etzhayyim.legalEntity.stats` | query | 総数 + 国別 + ソース別カウント |
| `com.etzhayyim.legalEntity.search` | query | 法人名 exact match 検索 |

### 使用例

```bash
# 200 LEI を batch ingest (1 page)
curl -X POST https://le9k4x2m.etzhayyim.com/xrpc/com.etzhayyim.legalEntity.collectGlobal \
  -H "Content-Type: application/json" -d '{"pages":1,"pageSize":200}'

# 統計
curl -X POST https://le9k4x2m.etzhayyim.com/xrpc/com.etzhayyim.legalEntity.stats \
  -H "Content-Type: application/json" -d '{}'
```

## Coverage Status

固定値はすぐ古くなるので、この app の現況確認は live query 前提にする。

- vertex coverage: `com.etzhayyim.legalEntity.stats`
- relation coverage: `edge_legal_entity_mentions` / `edge_legal_entity_relates_to` / `edge_legal_entity_owns` / `edge_legal_entity_trades_with`
- statement / filing coverage: `vertex_public_statement` / `vertex_company_filing` / `vertex_company_fact`

### Coverage ツール表示修正 (TODO)

`etzhayyim coverage world` は `vertex_did` の path-based DID をカウントする。現状 records のみ書いて DID 未登録のため 0 表示。修正:
1. `collectGlobal` に `sdk.pds.identityCreate("lei:{LEI}", ...)` を追加
2. または `world_coverage.go` に `vertex_legal_entity` 直接 COUNT query を追加

## Infrastructure Dependencies

| コンポーネント | 変更 | ファイル |
|---|---|---|
| `@etzhayyim/graph-schema` helpers.ts | `'LegalEntity': 'vertex_legal_entity'` 追加 | `30-graph/graph-schema/src/helpers.ts` |
| `@etzhayyim/graph-schema` database.ts | `VertexLegalEntityRow` + `Database` entry 追加 | `30-graph/graph-schema/src/database.ts` |
| PDS core.ts | `buildTypedVertex` に `LegalEntity` case 追加 | `50-infra/cloudflare/workers/atproto/src/core.ts` |
| RisingWave DDL | `vertex_legal_entity` テーブル (27 columns) | 既存 (raw SQL bootstrap) |
| Lexicon | collectors + identity + disclosure / relation records | `00-contracts/lexicons/com/etzhayyim/apps/legalEntity/` |

## W Protocol Channels

| channel | 用途 |
|---|---|
| `le-feed` | 新規法人登録・更新フィード (default) |
| `le-alerts` | 異常検知 (dissolved, struck off, sanctions match) |

## Cross-actor Integration

- `ubo.etzhayyim.com` — 法人の UBO 分析トリガー
- `malak.etzhayyim.com` — 制裁リスト・反社照合
- `resources.etzhayyim.com` — entity graph 統合
- `natural-person.etzhayyim.com` — 法人 workforce demographics 連携

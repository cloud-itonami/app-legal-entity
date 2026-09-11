# app-legal-entity

**この repo には「legal entity を扱う実装」が 3 つ入っていて、互いに別物である。**
どれか 1 つが正本で残りが古い、という関係ではない —— 3 つとも別の層を
別の言語で書いたもので、**表面（何が呼べるか）が一致していない**。

| サブツリー | 何か | 手元で動くか |
|---|---|---|
| **`wasm/…-le9k4x2m/` + `src/`** | **appview**（Cloudflare Worker）。XRPC を MCP router に中継する薄い edge。**2026-08-18 に TypeScript/Svelte から ClojureScript へ移行**（[ADR-0001](docs/adr/0001-migrate-the-appview-from-typescript-to-clojurescript.edn)） | **動く**（8 tests / 69 assertions、build 0 warnings、smoke 47 checks、workerd 実走） |
| **`kotoba/`** | TypeScript。`@etzhayyim/sdk` の AT PDS レコードの上に建てた**登記レジストリ 9 関数**（entity / filing / ownership edge / coverage） | **動く**（typecheck exit 0、vitest **4 passed**。2026-08-17 実測） |
| **`lg-clj/`** | Clojure。Python LangGraph サーバの移植で、**17 個の StateGraph**（health + 16 collector）とその dispatch | **動く**（`bb test` exit 0、**15 tests / 58 assertions**。2026-08-17 実測） |

**そして [`CLAUDE.md`](CLAUDE.md) は、この 3 つのどれでもない 4 つ目を記述している。**
あちらが書くのは RisingWave に 27 列の `vertex_legal_entity` を投影する
**デプロイ済みの dispatcher 側システム**（19 コレクタ・GLEIF 3.0M 件）で、
**その実装ファイルはここに 1 つも無い**。`CLAUDE.md` を**設計の正本**として読むのは
正しい。**この repo の中身の説明として読むと必ず間違える。**
（移行で偽になった `## Runtime` 節には 2026-08-18 に訂正を入れた。）

この README が書くのは設計ではなく、**いま実際に何が在って、何が動いて、
何が壊れているか**である。手順は [`docs/operator-quickstart.md`](docs/operator-quickstart.md)。
数値はすべて `scripts/verify-docs-claims.cljk` が tree から再計算して検査する。

## 1. appview — deploy されるものは、いま読んでいるソースである

```
src/legal_entity/route.cljk    判断（どの handler が答えるか）  ← 純 .cljc、テスト対象
src/legal_entity/view.cljk     ページ（jp-go-dds の hiccup）    ← 純 .cljc、テスト対象
src/legal_entity/worker.cljk   Request/Response に触る唯一の層
        ↓ shadow-cljs :target :esm
dist/worker.js                 ← wasm/…/wrangler.jsonc の "main" が指すもの
```

**移行前は `main` が `svelte/.svelte-kit/cloudflare/_worker.js` を指していた ——
tree に 1 ファイルも存在しないビルド出力である**（`git ls-files` に `svelte-kit`
は 0 件）。一方 `src/app.ts`（読み手が最初に開くファイル）は**どの wrangler
config からも参照されておらず**、それが使う `DISPATCHER_URL` /
`DISPATCHER_INTERNAL_SECRET` は `wrangler.jsonc` に binding が無かった。
いまは `main` が指す bundle が上のソースからコンパイルされたものなので、その形は
構造的に起こり得ない —— 検証器が **shadow の出力先と wrangler の `main` と
export の ns 名の 3 つが噛み合っていること**を検査する。

**`wrangler.jsonc` と `kotodama.jsonld` だけが `wasm/…-le9k4x2m/` に残る**
（deploy 単位なので）。ソースとビルド設定は repo 直下。だから `main` は
`../../dist/worker.js` である。同型移行の app-ongakuka / app-lo / app-po /
app-cowork / app-analytics が全て同じ形。

### 公開ルート

| METHOD | PATH | 何をするか |
|---|---|---|
| GET | `/` | この appview の説明ページ |
| GET | `/health` | 生存確認。deploy された面が答えることを外から確かめられる |
| POST | `/xrpc/:nsid` | XRPC を MCP router へ中継する |
| OPTIONS | `/xrpc/*` | CORS preflight |

**この表の出所は `legal-entity.route/routes` で、ページもそこから描く。** 移行前の
`+page.svelte` は `routeCount: 0` / `routes: []` / `vars: []` を literal で持って
おり、隣の `wrangler.jsonc` が **route 1・var 9・capability 5** を宣言していることに
気づけなかった（訪問者には『No public route is declared』と表示していた）。

多段パス（`/xrpc/a/b`）は**移行前と同じくそのまま転送する**。deploy されていた
SvelteKit の route は rest parameter `[...path]` で受けており、空文字だけを 400
（`Missing XRPC method`）にしていた。**絞るのは移行ではなく方針変更**である。

### `APP_CAPABILITIES` は NSID ではない（この repo だけの違い）

同型移行の repo（app-air-cargo 等）は `APP_CAPABILITIES` に NSID prefix を足して
完全修飾 NSID としてページに印字する。あちらの値が camelCase の**メソッド名**
だからで、それは正しい。**この repo の値は違う** ——

    ["gleif-lei-ingest" "corporate-registry" "legal-entity-search"
     "multi-country-ingest" "entity-resolution"]

は `kotodama.jsonld` の `profile.capabilities` と**1 バイト違わず同一**の
能力ラベルで、メソッド名ではない。**deploy されていたコードはこの var を
一度も読んでいない**（`wasm/` 配下で出るのは `wrangler.jsonc` の宣言 1 箇所だけ）。
実在する NSID は `kotodama.jsonld` の `triggers.subscribeRepos.collections` の
**8 本**で、5 つの capability 文字列はそのどれの末尾セグメントでもない。

だから prefix を足さない。ページは**別々の表**に描く。smoke は 5 本 × 2 方向で
検査する（ラベルが出ること / prefix 付きの捏造 NSID が出ていないこと）。

### ページが出す値・出さない値

env の**キー名**は出すが、値は出さない —— **中継先を除いて**。
`AGENTGATEWAY_MCP_ROUTER_URL` の値だけは、どこへ中継するかを運用者が見る
必要があるので意図的に表示する。

smoke はこれを**同じ var に当てた 2 つの番兵**で見る: `APP_UI_TYPE` の
**値**が出ていないこと、その**キー**が出ていること。別の var に置くと、
ページがその var をそもそも描いていないという理由で「合格」しうる。
三つ目の番兵（`wrangler.jsonc` に無いキー）が、env を**焼かずに列挙している**
ことを言う。

## 2. UI

基盤は `kotoba-lang/jp-go-digital-design-system`（デジタル庁デザインシステム）。
色・寸法は `--hig-*` トークン契約だけで書き、raw hex も px フォントサイズも
置かない。app 固有 CSS は 3 行。CSS は外部リクエストゼロの方針どおり
`shadow.resource/inline` で bundle に焼く。

決定論的 audit（`kotoba-lang/design-quality`）で **100.00 / 100**（gate 95）、
`--extra-axes` の 12 軸でも 100.00。

**ただしこのスコアは design system の有無を見ていない。** 実測（このページ、
2026-08-18）: **CSS を 1 バイトも渡さずに描いた同じページが 96.63 で `--min 95` を
通る。** だから smoke は 2 本に割ってある:

| 探す文字列 | CSS 込み | CSS 無し | 何を言うか |
|---|---|---|---|
| `dads-table` | 77 | **9**（0 にならない） | view がライブラリを呼んだ |
| `--color-primitive-blue` | 45 | **0** | stylesheet が実際に bundle に入った |

（`grep -o … \| wc -l` で数えた出現回数。`grep -c` は**行数**を返すので
別の数になる —— 同じページで `dads-table` は 77 occurrences / 74 lines。）

前者だけを見る検査は**落ちようがない** —— それは view が出す markup であって、
CSS の有無と無関係に現れる。CSS を外して再ビルドすると後者だけが赤くなることを
確認済み。

## 3. 3 つの表面は一致していない（実測）

| | collector の数 | 登記 CRUD | 備考 |
|---|---|---|---|
| `CLAUDE.md`（4 つ目のシステム） | **19**（`collect*` コマンド） | 無し（`stats` / `search` のみ） | RisingWave 投影あり |
| `lg-clj/` | **16**（`registryCollect*` 12 + gleif 2 + edgar 2） | 無し | 投影なし・handler 未配線 |
| `kotoba/` | **0** | **9 関数** | AT PDS レコードのみ |
| appview（`src/`） | **0** | 無し | 中継のみ。業務ロジックを持たない |

`lg-clj` に無い 6 か国（**Bra / Bel / Aus / Can / Zaf** と bulk 系）は、
`CLAUDE.md` 側で "Bulk-required LangServer contract" ないし "Unsupported" と
書かれているものと**一致する** —— 移植漏れではなく意図的に落とした 5 件と
`collectGlobal`（GLEIF、`gleifFetchPages` として存在）である。

**一方 `kotoba/` と他の 2 つの間には対応が無い。** `kotoba/` は「収集する」実装
ではなく「登記簿を持つ」実装で、`registerEntity` / `addFiling` /
`recordOwnership` は `CLAUDE.md` にも `lg-clj` にも対応するコマンドが無い。

## 4. 測って分かった欠陥（2026-08-17〜18）

### A. `CLAUDE.md` が名指しするホストは 4 つとも DNS を引けない（移行では直らない）

| ホスト | 役割 | DNS |
|---|---|---|
| `le9k4x2m.etzhayyim.com` | 公開ホスト（wrangler の route） | **応答なし** |
| `legal-entity.etzhayyim.com` | actor DID のホスト | **応答なし** |
| `mcp.etzhayyim.com` | `/xrpc/:nsid` の中継先 | **応答なし** |
| `dispatcher.etzhayyim.com` | 撤去した `app.ts` の転送先 | **応答なし** |

apex の `etzhayyim.com` は引ける（`172.67.179.128` / `104.21.51.111`）ので、
ドメインごと消えたのではなくサブドメインが無い。deploy 先も中継先も、いま存在
しない。`/xrpc/` は到達できなければ **502 を返す** —— 成功と同じ形で隠さない
（移行前は framework の 500 HTML エラーページだった）。

### B. 改名の記録が読めない —— 矢印の両側が同じ文字列

`SUBSTRATE-PORT-PENDING.md` は「何を何に改名したか / 残っているか」を矢印で
書いているが、**3 箇所とも左右が同一文字列**である（一括置換が置換の記録
そのものにも当たった）。旧名が消えているので、**この文書からは「何が残っているか」を
復元できない。** 旧名は `etzhayyim/root` の rev `e5654f08` 側にしか無い。

移行でこの文書の §1 §2 §5 は「移植」ではなく「撤去」で解決したので更新したが、
**§2 が求めていた re-target 自体は依然として未了**である（転送先は
`wrangler.jsonc` の `AGENTGATEWAY_MCP_ROUTER_URL` に移っただけで、値は変えていない）。

### C. ~~`wasm/` サブツリーはこの repo から install できない~~ → **解消**（撤去）

移行前は `wasm/…/package.json` が `"@etzhayyim/kotodama-host-sdk": "workspace:*"` を
要求するのに repo にワークスペース根が無く、`npm install` が
`EUNSUPPORTEDPROTOCOL` で落ちていた。**その `package.json` は appview の
TypeScript もろとも撤去した** —— cljs の依存は `deps.edn` に在る。

### D. `bb.edn` は退役した script host を前提にしている（未解消）

`lg-clj/bb.edn` と `run_tests.clj` は babashka（`bb`）で走る。**実際に動く**が、
workspace 規約 ADR-2607173000 は script host を **nbb** に一本化しており、
`bb.edn` を新規に置かないことになっている。既存物なのでそのままだが、
**この repo に新しいスクリプトを足すときに `bb` を増やさない**（移行で足した
`scripts/*.cljs` は 2 本とも nbb）。

## 5. 成熟度スキャナから見えること・見えないこと

fleet の成熟度スキャナ（`scripts/itonami-maturity-scan.cljs`）は
**リポジトリ直下の `src/` と `test/`** を数える。**移行で appview のソースが
repo 直下に来たので、この repo の substrate と test は初めて 0 バイトでなくなる。**

**これは計測器に合わせて動かしたのではない。** 直下に置いたのは同型移行 5 本と
同じ形だからで、スキャナの都合ではない。`kotoba/src` と `lg-clj/src` は依然として
`src/` の外なので、**それらは相変わらず 0 として測られる** —— 移した理由が
計測でないことは、この非対称がそのまま示している。

## 6. 由来（custody）

`migration.edn` は出所を `etzhayyim/root` rev `e5654f08`（26 ファイル /
72,373 バイト）と宣言する。移行後の状態:

- 継承したまま **1 バイトも変わっていない** 2 ファイル（`README.edn` /
  `wasm/…/kotodama.jsonld`、計 2,373 バイト）—— sha256 を検証器に固定
- **意図的に変更した** 4 ファイル: `wasm/…/wrangler.jsonc`（`main` の付け替え、
  `assets` と `rules` の撤去、`compatibility_flags` の撤去、`APP_FRAMEWORK` の更新）/
  `migration.edn`（追加・撤去・kept を登録）/ `CLAUDE.md`（Runtime 節）/
  `SUBSTRATE-PORT-PENDING.md`。**内容で検査する** —— 意図的な変更と careless な
  変更を区別するため
- **撤去した 9 ファイル**（288 行）は `:removed-by-migration` に名指しで登録。
  検証器がその 9 パスの**不在**を検査する（byte 合計は「TS が消えた」と言えない）
- **`:allowed-additions` を更新した。** 移行前は切り出し時の 2 件しか無く、
  `README.md` と `docs/operator-quickstart.md` が未登録の追加として浮いていた
  （旧 README がそう書いていた）。いま 15 件を登録し、検証器が
  「登録されていない追加ファイル」を落とす

### 消さなかったもの — `kotoba/` と `lg-clj/`

移行が消したのは **appview の** TypeScript と Svelte であって、この repo の
TypeScript 全部ではない。`kotoba/`（7 ファイル / 25,274 バイト）と
`lg-clj/`（6 ファイル / 14,846 バイト）は**どちらも appview から参照されて
いない**（実測: 相互参照 0 件）。bundle には入らないが、この移行が使う基準で
「死んで」はいない —— 依存は解決し、テストは通る。**消すのは移行ではなく破壊**
なので残した。

13 ファイルは `migration.edn` の `:kept-not-the-appview` に登録し、検証器が
**per-file sha256 と件数とバイト数**で固定する —— 黙って増える（appview の
TypeScript が library のふりをして戻る）ことも、黙って腐ることもできない。

**repo 全体の TypeScript は 8 → 5 になった。** 減った 3 本が appview のもので、
残る 5 本が `kotoba/` である。

## 7. まだ直していないこと

- **A（DNS）** — 生きているホストがどれなのかを知らない。`CLAUDE.md` の
  デプロイ記述を消すべきか別のホスト名に直すべきかは、この repo の外の事実に依存する。
  **この移行では deploy していない**（`wrangler dev --local` までで止めた）。
- **B（改名記録）** — 旧名を復元するには `etzhayyim/root` rev `e5654f08` を読む必要がある。
- **D（`bb.edn`）** — 既存物として残置。
- `kotoba/` のテストは 4 ケースで、**`listEntities` の cursor / limit 200 上限、
  `coverage` の `maxScan` と `truncated`、`getEntity` の `notFound`、
  `listFilings` の `since` 絞り込み**は 1 度も通っていない。
- **`/xrpc`（末尾スラッシュ無し）を 400 にしたが、SvelteKit の `[...path]` が
  0 セグメントにマッチするかは実測していない**（`svelte/` の install は
  `svelte-kit sync` を伴い副作用が大きいので走らせていない）。参照実装 2 本が
  ここで食い違っており、多数派の app-air-cargo 側に合わせた。

## 8. 検証

```bash
npx --yes nbb scripts/verify-docs-claims.cljk .   # <dir> は先頭に置く
```

exit 0 = 全一致 / 1 = 食い違い / **2 = 判定できなかった**（0 と区別する）。
テスト・ビルド・smoke・workerd 実走は [`docs/operator-quickstart.md`](docs/operator-quickstart.md)。

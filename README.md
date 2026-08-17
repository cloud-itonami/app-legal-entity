# app-legal-entity

**この repo には「legal entity を扱う実装」が 3 つ入っていて、互いに別物である。**
どれか 1 つが正本で残りが古い、という関係ではない —— 3 つとも別の層を
別の言語で書いたもので、**表面（何が呼べるか）が一致していない**。

| サブツリー | 何か | 手元で動くか |
|---|---|---|
| **`kotoba/`** | TypeScript。`@etzhayyim/sdk` の AT PDS レコードの上に建てた**登記レジストリ 9 関数**（entity / filing / ownership edge / coverage） | **動く**（typecheck exit 0、vitest **4 passed**） |
| **`lg-clj/`** | Clojure。Python LangGraph サーバの移植で、**17 個の StateGraph**（health + 16 collector）とその dispatch | **動く**（`bb test` exit 0、**15 tests / 58 assertions**） |
| **`wasm/…-le9k4x2m/`** | Svelte + Cloudflare Worker の**薄いプロキシ**（XRPC を dispatcher に転送するだけ） | **動かない** —— install が `EUNSUPPORTEDPROTOCOL` で落ちる（§3-C） |

**そして [`CLAUDE.md`](CLAUDE.md) は、この 3 つのどれでもない 4 つ目を記述している。**
あちらが書くのは RisingWave に 27 列の `vertex_legal_entity` を投影する
**デプロイ済みの etzhayyim 側 Worker**（19 コレクタ・GLEIF 3.0M 件・
`le9k4x2m.etzhayyim.com`）で、**その実装ファイルはここに 1 つも無い**。
これは私の推測ではなく、この repo 自身の
[`SUBSTRATE-PORT-PENDING.md`](SUBSTRATE-PORT-PENDING.md) §6 が
「CLAUDE.md は依然として etzhayyim 側の書き込み経路を記述している」と
書いている（§3-A）。

`CLAUDE.md` を**設計の正本**として読むのは正しい。**この repo の中身の説明として
読むと必ず間違える。**

この README が書くのは設計ではなく、**いま実際に何が在って、何が動いて、
何が壊れているか**である。手順は [`docs/operator-quickstart.md`](docs/operator-quickstart.md)。

## 1. この repo に在るもの（28 ファイル）

`etzhayyim/root` の `60-apps/etzhayyim-project-legal-entity`（rev `e5654f08`、
26 ファイル / 72,373 バイト）から切り出した standalone artifact
（[`migration.edn`](migration.edn)）。`README.edn` と `migration.edn` が追加の
2 件で、**この `README.md` と `docs/operator-quickstart.md` はさらに後から
足している** —— `migration.edn` の `:allowed-additions` はこの 2 件を
まだ列挙していないので、そこは切り出し契約の更新漏れである（fleet の他の repo
と同じ扱い）。

### `kotoba/` — AT PDS 上の登記レジストリ（TypeScript）

| ファイル | 中身 |
|---|---|
| `src/types.ts`（7,320 B） | レコード型 3 種、`EntityStatus` 3 値、検証 `isJurisdiction`（ISO 3166-1 **alpha-2**）/ `isLei`（20 字）/ `isPermille`（0..1000 の整数）、DID・rkey 生成 |
| `src/registry.ts`（11,750 B） | 本体 9 関数。コレクションは `…legalEntity.legalEntity` / `.companyFiling` / `.entityOwnership` の 3 本 |
| `src/index.ts`（703 B） | barrel |
| `test/legal-entity.test.ts`（4,195 B） | `MockEtzhayyim` に対する 4 ケース |

**AT Lexicon に float が無いので、持分は千分率（per-mille, 0..1000）の整数**で
持つ。これは設計上の制約であって端数処理の都合ではない（`types.ts` 冒頭）。

外部キーは実際に検査される: `addFiling` は entity の存在を、`recordOwnership` は
**owner と owned の両方**を確認し、自己ループ（`owner === owned`）を拒否する。

### `lg-clj/` — LangGraph サーバの Clojure 移植

| ファイル | 中身 |
|---|---|
| `src/lg_legal_entity/server.cljc`（4,823 B） | `GRAPHS`（17）・`NSID-MAP`・`dispatch-run` / `dispatch-xrpc` / `health` |
| `src/lg_legal_entity/graphs/task.cljc`（2,960 B） | 1 ノード collector graph の生成器 + **注入可能な `*handlers*` seam** |
| `src/lg_legal_entity/graphs/health.cljc`（761 B） | health graph |
| `test/lg_legal_entity/smoke_test.cljc`（5,032 B） | 15 ケース / 58 アサーション |

**collector は既定で外部に出ない。** `*handlers*` が空のとき
`default-handler` が `{:status "not-configured"}` を返すので、
**registry API にも RisingWave にも触れずにグラフが読み込めて invoke できる**
（`task.cljc` の docstring が「substrate が RisingWave を禁じるので
handler を注入 seam にした」と明記）。テストはここに stub を差して
dispatch と例外処理を検査している。

### `wasm/etzhayyim-wasm-legal-entity-le9k4x2m/` — 薄いエッジ

`src/app.ts`（4,454 B）は **XRPC を dispatcher に転送するだけ**で、
ビジネスロジックを持たない（`/health` 系 4 パスと `/xrpc/com.etzhayyim.legalEntity.*`
だけを見て、あとは 404）。Kysely も HyperDrive も無い。
`svelte/` は MCP ルータへ転送する SvelteKit BFF。

## 2. 3 つの表面は一致していない（実測）

| | collector の数 | 登記 CRUD | 備考 |
|---|---|---|---|
| `CLAUDE.md`（4 つ目のシステム） | **19**（`collect*` コマンド） | 無し（`stats` / `search` のみ） | RisingWave 投影あり |
| `lg-clj/` | **16**（`registryCollect*` 12 + gleif 2 + edgar 2） | 無し | 投影なし・handler 未配線 |
| `kotoba/` | **0** | **9 関数** | AT PDS レコードのみ |

`lg-clj` に無い 6 か国（**Bra / Bel / Aus / Can / Zaf** と bulk 系）は、
`CLAUDE.md` 側で "Bulk-required LangServer contract" ないし "Unsupported" と
書かれているものと**一致する** —— つまり移植漏れではなく、意図的に
落とした 5 件と `collectGlobal`（GLEIF、`gleifFetchPages` として存在）である。
ここは 3 つの中で唯一、ずれに説明が付く箇所。

**一方 `kotoba/` と他の 2 つの間には対応が無い。** `kotoba/` は
「収集する」実装ではなく「登記簿を持つ」実装で、`registerEntity` /
`addFiling` / `recordOwnership` は `CLAUDE.md` にも `lg-clj` にも対応する
コマンドが無い。同じ NSID 前缀（`com.etzhayyim.legalEntity.`）を使うが、
**コレクション名は 3 本とも `CLAUDE.md` の表に出てこない。**

## 3. 測って分かった欠陥（2026-08-17 UTC）

### A. `CLAUDE.md` が名指しするホストは 4 つとも DNS を引けない

`CLAUDE.md` は `le9k4x2m` を **DEPLOYED** と書き、`curl` の使用例を 2 つ載せる。
実際に引くと:

| ホスト | 結果 |
|---|---|
| `legal-entity.etzhayyim.com` | **Could not resolve host** |
| `le9k4x2m.etzhayyim.com` | **Could not resolve host** |
| `dispatcher.etzhayyim.com`（`app.ts` の既定転送先） | **Could not resolve host** |
| `mcp.etzhayyim.com`（`+server.ts` の既定転送先） | **Could not resolve host** |

apex の `etzhayyim.com` は引ける（Cloudflare、`104.21.51.111`）ので、
**ドメインごと消えたのではなくサブドメインが無い**。対照として
`opencorporates.com` は 200 を返すので、測定側のネットワークの問題ではない。

つまり `CLAUDE.md` の `curl` 例は**そのまま実行できない**し、
`wasm/` のエッジは既定設定では**転送先が存在しない**。

### B. 改名の記録が読めない —— 矢印の両側が同じ文字列

`SUBSTRATE-PORT-PENDING.md` は「何を何に改名したか / 残っているか」を
矢印で書いているが、**3 箇所とも左右が同一文字列**である:

```
ACTOR_DID etzhayyim.com → etzhayyim.com                    (§What's done)
mcp.etzhayyim.com → 再ターゲット先 mcp.etzhayyim.com          (§残り 2)
@etzhayyim/kotodama-le9k4x2m → @etzhayyim/kotodama-le9k4x2m  (§残り 5)
```

同じ形がソース側のコメントにも入っている（`wasm/…/src/app.ts` 冒頭、
`svelte/src/routes/xrpc/[...path]/+server.ts` 冒頭）。**一括置換が、
置換の記録そのものにも当たった**結果と読める —— 旧名が消えているので、
**この文書からは「何が残っているか」を復元できない。**

これは体裁の問題ではない。§残り 2 と §残り 5 は**未完了**として列挙されている
のに、何をすればよいかが書かれていない状態になっている。旧名は
`etzhayyim/root` の rev `e5654f08` 側にしか無い。

### C. `wasm/` サブツリーはこの repo から install できない

```
npm error code EUNSUPPORTEDPROTOCOL
npm error Unsupported URL Type "workspace:": workspace:*
```

`wasm/…/package.json` が `"@etzhayyim/kotodama-host-sdk": "workspace:*"` を
要求するが、**この repo にワークスペース根が無い**（リポジトリ直下に
`package.json` も `pnpm-workspace.yaml` も無い）。切り出しでワークスペースの
文脈が失われたまま、依存の書き方だけが残っている。

`svelte/` 側は通常の semver 依存なので、この問題は親ディレクトリだけにある。

### D. `bb.edn` は退役した script host を前提にしている

`lg-clj/bb.edn` と `run_tests.clj` は babashka（`bb`）で走る。**実際に動く**
（§手順）が、workspace 規約 ADR-2607173000 は script host を **nbb** に
一本化しており、`bb.edn` を新規に置かないことになっている。ここは既存物なので
そのままだが、**この repo に新しいスクリプトを足すときに `bb` を増やさない**。

## 4. 成熟度スキャナから見えないこと（測定の盲点）

fleet の成熟度スキャナ（`scripts/itonami-maturity-scan.cljs`）は
**リポジトリ直下の `src/` と `test/`** を数える。この repo の Clojure は
`lg-clj/src` と `lg-clj/test` に、TypeScript は `kotoba/src` と `kotoba/test`
に在るので、**substrate も test も 0 バイトとして測られる**。

実際には `.cljc` が 8,544 バイト（3 ファイル）、テストが 5,032 バイト
（1 ファイル）在る。**これは測定の盲点であって、実装やテストが無いという
意味ではない。** ディレクトリを移せば数字は動くが、それは計測器に合わせた
移動であって内容の改善ではないので、していない。

## 5. 依存の出所

`kotoba/package.json` の依存 2 本は **public repo の commit 固定**:

| 依存 | 固定先 |
|---|---|
| `@etzhayyim/sdk` | `github.com/etzhayyim/com-etzhayyim-sdk` `#12314a0c` |
| `@etzhayyim/sdk-mock` | `github.com/etzhayyim/com-etzhayyim-sdk-mock` `#c857ff9b` |

これらは推移的に `kotoba-lang/{ipfs,atproto-client,checkpointer,base-l2}` 等を
引き、**`added 135 packages`**（`node_modules` 直下のエントリ数としては 75）に
なる。`etzhayyim` 組織への特別なアクセス権は要らない。

**ロックファイルは追跡されていない。** `package-lock.json` も `.gitignore` も
無いので、`npm install` を踏むと未追跡ファイルが 2 つ残り、推移依存の版は
固定されていない（直接依存 2 本だけが SHA 固定）。

## 6. まだ直していないこと

この README は**測っただけ**で、上の欠陥を 1 つも修正していない。

- **A（DNS）** — 生きているホストがどれなのかを知らない。`CLAUDE.md` の
  デプロイ記述を消すべきか、別のホスト名に直すべきかは、この repo の外の
  事実に依存する。
- **B（改名記録）** — 旧名を復元するには `etzhayyim/root` rev `e5654f08` を
  読む必要がある。
- **C（`workspace:*`）** — ワークスペース根を足すか、依存を実 URL に
  差し替えるかは、`@etzhayyim/kotodama-host-sdk` の配布先を知っている人の判断。
- **`.gitignore` / lockfile** が無い問題も直していない。
- `kotoba/` のテストは 4 ケースで、**`listEntities` の cursor / limit 200 上限、
  `coverage` の `maxScan` と `truncated`、`getEntity` の `notFound`、
  `listFilings` の `since` 絞り込み**は 1 度も通っていない。

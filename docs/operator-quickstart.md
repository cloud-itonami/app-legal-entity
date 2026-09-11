# operator quickstart — app-legal-entity

**この手順は 2026-08-18 UTC に上から順に実際に実行し、exit code と出力を
突き合わせてある。** 数字（パッケージ数・所要時間・ミリ秒）は 1 回の実測値で
あって契約ではない。**exit code と件数は契約として読んでよい。**

計測に使った環境: macOS（darwin 25.3.0, arm64）/ **node v26.3.0** / **npm 11.16.0** /
**nbb v1.4.208** / **babashka v1.12.218**（`lg-clj` のみ）。

この repo に**デプロイ手順は無い**（§7）。`CLAUDE.md` が書くデプロイ先は 4 ホストとも
DNS を引けない（README §4-A）。

**この repo は 3 つの独立したサブツリーを持ち、それぞれ別のビルド根を持つ**
（README §1）。§1〜§6 が appview（2026-08-18 に cljs へ移行）、§A が `kotoba/`、
§B が `lg-clj/`。

---

## 1. 取得して、書いてあることが本当か検査する

```bash
git clone git@github.com:cloud-itonami/app-legal-entity.git
cd app-legal-entity
REPO=$PWD
npx --yes kbb --backend sci scripts/verify-docs-claims.cljk .
```

**実測**（末尾）:

```
SCANNED	31
PASS	tracked-files	expected=31	actual=31
...
PASS	adr-is-tx-data	expected=true	actual=true
CHECKED	40
OK	every claim in README.md and docs/operator-quickstart.md holds
```

exit 0。**exit 2（UNDETERMINED）は 0 ではない** —— tree を読み切れなかったという
別の答えで、「検査して問題なし」と混ぜない。

⚠ **`<dir>` は引数の先頭に置く。** このスクリプトは `--` で始まらない最初の引数を
tree のパスとして取るので、`... --min 10 .` と書くと **`"10"` がパスになる**。

この検査には移行の不変条件が入っている: appview の TypeScript / Svelte が戻って
いないこと（撤去した 9 パスの不在 + `.ts` の総数）、`wrangler.jsonc` の `main` が
shadow の出力先を指していること、ページが route 表から描かれていること、
`kotoba/` と `lg-clj/` の 13 ファイルが 1 バイトも動いていないこと。

---

## 2. テストを走らせる（ビルド不要・ブラウザ不要）

判断（`route.cljc`）と描画（`view.cljc`）は純 `.cljc` なので、nbb だけで回る。

```bash
K=~/github/com-junkawasaki/orgs/kotoba-lang
CP="src:test:$K/jp-go-digital-design-system/src:$K/html/src:$K/css/src"
cat > /tmp/run.cljs <<'EOF'
(require '[cljs.test :refer [run-tests]] 'legal-entity.route-test)
(run-tests 'legal-entity.route-test)
EOF
npx --yes kbb --backend sci --classpath "$CP" /tmp/run.cljs
```

**実測**:

```
Testing legal-entity.route-test

Ran 8 tests containing 69 assertions.
0 failures, 0 errors.
```

何を固定しているか: `/xrpc/` は**空の nsid だけ** 400 にする（`/xrpc/a/b` は移行前の
rest parameter と同じく転送する）、MCP router の URL 解決（空白だけの設定は未設定と
して扱う）、`result` / `structuredContent` の剥がし方、**capability に NSID prefix を
足さないこと**、`collections` の 8 本が実在する NSID であること、そして
**ページが渡された値から描かれること**（固定値を焼いていたら落ちる）。

⚠ **classpath の design system は west checkout から引く。** この walk の時点で
`kotoba-lang/jp-go-digital-design-system` の west checkout は `2e2d191` で clean、
これは `deps.edn` の pin とも remote HEAD とも一致していた（実測）。**この一致は
常に成り立つわけではない** —— 同日の app-air-cargo 移行では west checkout が
pin より 6 日遅れており、pin の抽出に対して測り直す必要があった。pin を触るなら
測り直すこと。

---

## 3. ページを描画して採点する

```bash
K=~/github/com-junkawasaki/orgs/kotoba-lang
CP="src:$K/jp-go-digital-design-system/src:$K/html/src:$K/css/src"
cat > /tmp/render.cljs <<'EOF'
(require '["node:fs" :as fs] '[legal-entity.view :as view] '[legal-entity.route :as route])
(let [css (.readFileSync fs (str (.-DDS js/process.env) "/resources/jp_go_dds/dds.css") "utf8")]
  (.writeFileSync fs "/tmp/le-page.html"
    (view/render {:css css :routes route/routes
                  :capabilities (route/capability-labels
                                  ["gleif-lei-ingest" "corporate-registry" "legal-entity-search"
                                   "multi-country-ingest" "entity-resolution"])
                  :collections route/collections
                  :vars [:APP_NANOID :APP_UI_TYPE :AGENTGATEWAY_MCP_ROUTER_URL]
                  :mcp-url "https://mcp.etzhayyim.com/xrpc/com.etzhayyim.mcp.message"
                  :actor route/actor-did}))
  (println "ok"))
EOF
DDS="$K/jp-go-digital-design-system" npx --yes kbb --backend sci --classpath "$CP" /tmp/render.cljs

cd $K/design-quality && npx --yes kbb --backend sci -m design-quality.cli score /tmp/le-page.html --min 95
```

**実測**:

```
  100.00  /tmp/le-page.html
aggregate: 100.00
axes scored: 10 (viewport, safe-area, dynamic-viewport, tap-targets, focus-visible,
                 reduced-motion, overflow-guard, color-scheme, responsive, semantics)
NOT scored: input-zoom, contrast — pass --extra-axes to include the optional ones
A pass says nothing about an axis that was not applied.
gate: aggregate 100.00 >= min 95.00 -> PASS
```

`--extra-axes` を足すと **12 軸すべてで 100.00**、gate PASS。

### ⚠ この 100.00 は design system について何も言っていない

**同じページを CSS 抜きで描いて採点した**（`:css ""`）:

```
aggregate: 96.63
gate: aggregate 96.63 >= min 95.00 -> PASS
```

**デザインシステムを 1 バイトも入れなくても gate を通る。** だから「入っている」
ことは smoke（§5）の 2 本目でしか言えない。同じページでの実測:

| 探す文字列 | CSS 込み | CSS 無し |
|---|---|---|
| `dads-table` | 77 | **9**（0 にならない） |
| `--color-primitive-blue` | 45 | **0** |

（`grep -o … \| wc -l` で数えた**出現回数**。`grep -c` は**行数**を返すので
同じページで `dads-table` は 74 になる —— どちらで数えたかを書かないと比較できない。）

`dads-table` は view が出す markup なので CSS の有無と無関係に現れる ——
**それを 1 本で見る検査は落ちようがない。**

---

## 4. bundle をビルドする

**高負荷ビルドは同時 1 本に制限されている**（superproject `CLAUDE.md` の
resource governor）。直接叩かず、必ず guard 経由で:

```bash
cd "$REPO"
node ~/github/com-junkawasaki/scripts/resource-guard.mjs run build -- \
  npx --yes amu compile --target wasm32-browser worker
ls -la dist/worker.js
```

lock を他セッションが持っていると **exit 2 で拒否される。迂回しない** ——
これはエラーではなく順番待ちである（この walk でも 2 回待った）。

**実測**:

```
[:worker] Build completed. (55 files, 12 compiled, 0 warnings, 8.61s)
-rw-r--r--  1 ... 256241 ... dist/worker.js
sha256: 46bd01400aa260a64ebae8beba55be94c66ba423eb03e0838efa5784fc66613a
```

### 壊れた var はビルドを **落とす**（両方向を実測した）

`shadow-cljs.edn` の `:compiler-options` に `:warnings-as-errors true` を入れてある。
入れる前は、存在しない var を参照しても shadow は **WARNING** を出して **exit 0** し、
壊れた bundle を書いていた ——「ビルドが通った」は検査ではなかった（**落ちようが
なかった**）。この repo で実際に両方向を見た。`worker.cljs:152` の `route/dispatch` を
存在しない `route/dispatch-typo` に改名して:

| | exit | `dist/worker.js` sha256 | 出力 |
|---|---|---|---|
| 改名前 | **0** | `46bd0140…fc66613a` | `0 warnings` |
| 改名後、option **あり** | **1** | `46bd0140…fc66613a`（**不変**） | `ERROR … Use of undeclared Var` |
| 改名後、option **なし** | **0** | `94a63a19…6a3c1d33`（**別物を出荷**） | `1 warnings` |
| 戻して再ビルド | **0** | `46bd0140…fc66613a` | `0 warnings` |

**落ちたビルドは bundle を出荷しない** —— sha256 が 1 バイトも動いていないことが
それを言っている。そして option を外すと**壊れたものが出荷される**。

#### ⚠ sha256 を比べるなら `.shadow-cljs` を消してから（実測 2026-08-18）

上の表のように bundle の sha256 を比べるときは、**必ず cold build にする**:

```bash
rm -rf .shadow-cljs dist
node ~/github/com-junkawasaki/scripts/resource-guard.mjs run build -- \
  npx --yes amu compile --target wasm32-browser worker
```

**shadow-cljs の `:esm` release 出力は、cold build では決定論的だが、
incremental build では同じソースから別のバイト列を出す。** 実測:

| ビルド | compiled | sha256 |
|---|---|---|
| cold（`.shadow-cljs` 削除、2 回とも） | 12 | `46bd0140…fc66613a` |
| incremental（ソース無変更、2 回とも） | 2 → 0 | `67c56d16…130f2bc0` |

ソース 4 ファイルと `deps.edn` と `dds.css` の sha256 が**すべて一致している状態で
出力だけが違う**（実際に `cmp` で確かめた）。

これは実害がある: 「backup から戻したあと再ビルドして sha を比べ、違えば
その backup は自分のものではなかった」という**共有 `/tmp` の汚染検査**が、
incremental build だと**偽陽性を出す**。この walk で実際に 1 回踏み、
cache を消して測り直すまで「隣の agent の backup を掴んだ」と誤診しかけた。

その出荷された bundle が何をするかも測った。**この repo では最初のリクエストで
`Cannot read properties of undefined (reading 'h')` を投げ**、smoke は
**exit 2（UNDETERMINED）**を返した —— 合格ではなく「判定できなかった」である。

キーは `:build-options` ではなく **`:compiler-options`** に置く。shadow が読むのは
`[:compiler-options :warnings-as-errors]` で、置き場所を間違えると**黙って無視される**。
実際に `:build-options` へ移してみると検証器の
`warnings-are-errors` と `warnings-as-errors-not-misplaced` が**両方**赤くなった。
**一方 `grep -q warnings-as-errors` はこの状態でも一致する**（実測: 文字列は
3 回現れ、うち 2 回はこの罠を説明するコメントの中）—— だから検証器は
このファイルを **EDN として読んで key の path を検査する**。

---

## 5. ビルドした成果物を実際に叩く

ここが deploy されるものに触る唯一の検査である。

```bash
cd "$REPO" && npx --yes kbb --backend sci scripts/smoke-worker.cljk dist/worker.js
```

**実測**（抜粋）:

```
PASS	default export has fetch	expected=true	actual=true
PASS	GET / status	expected=200	actual=200
PASS	page advertises capability gleif-lei-ingest	expected=true	actual=true
PASS	page does NOT fabricate an NSID for gleif-lei-ingest	expected=false	actual=false
PASS	page shows the KEY of the sentinel var	expected=true	actual=true
PASS	page hides the VALUE of that same var	expected=false	actual=false
PASS	page enumerates env keys it was handed	expected=true	actual=true
PASS	page uses the DADS table component	expected=true	actual=true
PASS	the DADS stylesheet is inlined in the bundle	expected=true	actual=true
PASS	multi-segment: same status as single-segment	expected=502	actual=502
PASS	/_app/meta was not carried over	expected=404	actual=404
CHECKED	47
OK	the built bundle answers as the route table says
```

exit 0。**bundle が無ければ exit 2**（「判定できなかった」であって合格ではない）。
`CHECKED 47` は実行本数の床（45）を超えたことを示す —— `doseq` が空 seq を回して
「0 件で合格」になるのを防ぐ。

---

## 6. Workers ランタイム（workerd）で動かす

Node で import する smoke より強い検査。実際の workerd で起こす。

```bash
cd "$REPO/wasm/etzhayyim-wasm-legal-entity-le9k4x2m"
npx --yes wrangler@latest dev --local --port 8791 --ip 127.0.0.1
# 別シェルで
curl -s -o /dev/null -w '%{http_code} %{content_type} %{size_download}\n' http://127.0.0.1:8791/
curl -s http://127.0.0.1:8791/health
```

**実測**:

```
200 text/html; charset=utf-8 85814
{"ok":true,"app":"legal-entity","runtime":"cljs",
 "actor":"did:web:legal-entity.etzhayyim.com","nanoid":"le9k4x2m",
 "routes":["/","/health","/xrpc/:nsid"],
 "capabilities":["gleif-lei-ingest","corporate-registry","legal-entity-search",
                 "multi-country-ingest","entity-resolution"],
 "collections":["com.etzhayyim.legalEntity.legalEntity", … 8 本]}
```

全ルート（実測、`compatibility_flags` **なし**の設定で）:

| 呼び出し | 実測 |
|---|---|
| `GET /` | 200 text/html、`--color-primitive-blue` 45 行・`/xrpc/:nsid` を含む |
| `GET /health` | 200 JSON（上記） |
| `POST /xrpc/` | 400 |
| `POST /xrpc/a/b` | 502 `{"error":"MCP router unreachable", …"url":"https://mcp.etzhayyim.com/…"}` |
| `OPTIONS /xrpc/x` | 204 |
| `GET /nope` | 404 |
| `GET /_app/meta` | 404 |
| `POST /health` | 405 |

`compatibility_flags`（`nodejs_compat` / `nodejs_als`）は SvelteKit の
adapter-cloudflare 由来で、この bundle には要らない。**撤去は憶測ではなく
この実測の後に行った。**

---

## 7. deploy —— この移行では**していない**

```bash
cd "$REPO/wasm/etzhayyim-wasm-legal-entity-le9k4x2m"
npx wrangler deploy
```

**ただし route が指す `le9k4x2m.etzhayyim.com` は DNS を引けない。** deploy が
成功しても誰も到達できない。`/xrpc/` の中継先 `mcp.etzhayyim.com` も同様なので、
到達できたとしても中継は **502 を返す**（成功と同じ形で隠さない）。

superproject の deploy guard は `origin/main` を含む checkout からの deploy しか
許さない点も併せて注意。

---

## A. `kotoba/` — TypeScript 登記レジストリ（**appview ではない。撤去していない**）

移行はこのサブツリーを**触っていない**。7 ファイルすべて sha256 で固定してある
（`scripts/verify-docs-claims.cljk` の `kept`）ので、1 バイト動けば §1 が落ちる。

### A-1. install

```bash
cd kotoba
npm install --no-audit --no-fund
```

**実測（2026-08-17）**: `added 135 packages in 2m`。大半の時間は git 依存 7 本の
`prepare: tsc` に行く。

#### ⚠ このマシンでは、ここで落ちる（repo の欠陥ではない）

npm **11.16.0** は、git 依存を準備するための内部 install で `--allow-scripts` を
受け付けない。`~/.npmrc` に `allow-scripts[]=@anthropic-ai/claude-code` の行が
あると、その設定が内部 install に継承されて **`EALLOWSCRIPTS` で落ちる**。
**環境の問題であって repo の問題ではない。** 回避:

```bash
grep -v '^allow-scripts' ~/.npmrc > /tmp/npmrc-no-allowscripts
npm install --userconfig /tmp/npmrc-no-allowscripts --no-audit --no-fund
```

#### ⚠⚠ install が**終わってから**次に進む

`node_modules/` にディレクトリが現れることは、install が終わったことを意味しない。
git 依存の `prepare: tsc` が走っている途中で `npm run typecheck` を叩くと
`TS2307: Cannot find module '@etzhayyim/sdk'` + 派生の `TS7006` 6 件が出る。
**repo の欠陥に見えるが、そうではない。** 判定は
`ls node_modules/@etzhayyim/sdk/dist/index.d.ts` が在るかで取れる。

### A-2. typecheck / test

```bash
npm run typecheck        # tsc --noEmit → 出力なし・exit 0
npm test                 # vitest run
```

**実測（2026-08-17）**: `Test Files 1 passed (1)` / `Tests 4 passed (4)`、exit 0、190–350 ms。

**この 4 ケースが触っていない経路**は README §7 に列挙した。

### A-3. 後片付け

`.gitignore` は移行で追加したので、`kotoba/node_modules/` と
`kotoba/package-lock.json` はもう未追跡ファイルとして残らない（移行前は残っていた）。

---

## B. `lg-clj/` — LangGraph サーバの Clojure 移植（**appview ではない**）

こちらも移行は触っていない。6 ファイルすべて sha256 で固定してある。

```bash
cd lg-clj && kbb -M:test
```

**実測（2026-08-17）**:

```
Testing lg-legal-entity.smoke-test

Ran 15 tests containing 58 assertions.
0 failures, 0 errors.
```

exit 0。**外部ネットワークにも RisingWave にも触らない** —— collector の handler は
既定で「未配線」スタブを返す注入 seam になっている。

⚠ `bb`（babashka）は workspace 規約 ADR-2607173000 で script host としては退役済み。
**この `bb.edn` は既存物なのでそのまま使うが、新しいスクリプトを `bb` で足さない**
（移行で足した `scripts/*.cljs` は 2 本とも nbb）。

---

## C. gate を落として確かめる

**緑を受け取る前に、赤くなることを見る。** 移行時に 9 通りの mutation を
**1 つずつ**当てた（2 つ同時に当てると互いを隠す）。各回のあとに復元し、
**bundle の sha256 が移行前の値に戻ることを確認**している —— 共有 `/tmp` で
隣の作業の backup を掴んでいないことの検査でもある。

| # | 壊し方 | 赤くなったもの |
|---|---|---|
| 1 | `route/dispatch` → 存在しない名前 | build exit 1、bundle 不変 |
| 1b | 同上 + `:warnings-as-errors` を外す | build exit 0 で**別の bundle を出荷**、smoke が exit 2 |
| 2 | `:compiler-options` → `:build-options` | `warnings-are-errors` と `warnings-as-errors-not-misplaced` の 2 件（grep なら緑） |
| 3 | `(rc/inline …)` → `""` | 『stylesheet is inlined』**のみ**。『uses the DADS table component』は**緑のまま** |
| 4 | env の値も一緒に描く（キーは残す） | 『hides the VALUE of that same var』**のみ** |
| 5 | env のキー一覧を焼く | 『enumerates env keys it was handed』**のみ** |
| 6 | capability に NSID prefix を足す | smoke 5 件 + 検証器 `capability-fn-does-not-prefix` + 単体テスト |
| 7 | 多段パスを 400 に絞る | smoke の multi-segment 4 件 |
| 8a | 撤去した `src/app.ts` を戻す | `removed-by-migration-absent` / `appview-ts-files` / `tracked-files` / `every-added-file-is-registered` |
| 8b | `.ts` を `kotoba/` に紛れ込ませる | `kept-kotoba-files` / `every-added-file-is-registered` / `tracked-files`（`appview-ts-files` は**正しく 0 のまま**） |
| 9 | kept ファイルを 1 文字だけ書き換え（**バイト数を保つ**） | `kept-files-unchanged` **のみ**（`kept-kotoba-bytes` は緑） |

### 外した変異は実演に数えない（2 回起きた）

- **#4 の初版**は `(keys e)` を `(vals e)` に置き換えたので、値が漏れると同時に
  **キーも消えた** —— 3 つの検査が同時に赤くなり、「値の露出」を単独で示せていない。
  キーを残したまま値も足す形に**再照準**した。
- **#9 の初版**はファイル末尾に行を足したので、hash より先に **`kept-kotoba-bytes`
  が落ちた** —— hash pin が効くことの証明になっていない。バイト数を変えない
  1 文字置換（`1000` → `1001`）に**再照準**した。

どちらも「壊したものと報告されたものが一致するまでやり直す」規律であって、
最初の観察を実演として数えていない。

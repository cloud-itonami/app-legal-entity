# operator quickstart — app-legal-entity

**この手順は 2026-08-17 UTC に上から順に実際に実行し、exit code と出力を
突き合わせてある。** 数字（パッケージ数・所要時間・ミリ秒）は 1 回の実測値で
あって契約ではない。**exit code と件数は契約として読んでよい。**

計測に使った環境: macOS（darwin 25.3.0, arm64）/ **node v26.3.0** / **npm 11.16.0** /
**babashka v1.12.218**。

この repo に**デプロイ手順は無い**。`CLAUDE.md` が書くデプロイ先は 4 ホストとも
DNS を引けない（README §3-A）。**この quickstart の完了条件は
「2 つのテストスイートが緑になること」**である。

3 つのサブツリーのうち **2 つは動き、1 つは動かない**（README §1）。
動かない 1 つも下に手順を書く —— 「試したら落ちた」を毎回やり直さないため。

---

## A. `kotoba/` — TypeScript 登記レジストリ

### A-0. 前提

必要なのは node と npm だけ。**etzhayyim 組織へのアクセス権は要らない**
（依存 2 本はどちらも public repo を commit SHA で固定している。README §5）。

### A-1. install

```bash
cd kotoba
npm install --no-audit --no-fund
```

**実測**: `added 135 packages in 2m`（npm cache は温まった状態）。大半の時間は
git 依存 7 本の `prepare: tsc` に行く。

⚠ **`135` と `ls node_modules | wc -l` の `75` は別の数え方**（後者は
`@etzhayyim/` などスコープを 1 エントリと数える）。install の成否は
npm が印字する方の数で見ること。

#### ⚠ このマシンでは、ここで落ちる（repo の欠陥ではない）

npm **11.16.0** は、git 依存を準備するための内部 install で `--allow-scripts` を
受け付けない。`~/.npmrc` に

```
allow-scripts[]=@anthropic-ai/claude-code
```

の行があると、その設定が内部 install に継承されて **`EALLOWSCRIPTS` で落ちる**。
**これは環境の問題であって、この repo の問題ではない。** だから `.npmrc` を
repo には足していない。回避は「その行だけ落とした userconfig を 1 回きり渡す」:

```bash
grep -v '^allow-scripts' ~/.npmrc > /tmp/npmrc-no-allowscripts
npm install --userconfig /tmp/npmrc-no-allowscripts --no-audit --no-fund
```

install の最後に出る

```
npm warn allow-scripts 8 packages have install scripts not yet covered by allowScripts:
```

は **警告であって失敗ではない**（`@etzhayyim/*` 7 本の `prepare: tsc` と
`@signalapp/libsignal-client` の 1 本）。exit code は 0 になる。

#### ⚠⚠ install が**終わってから**次に進む（ここで 1 度誤診した）

`node_modules/` にディレクトリが現れることは、install が終わったことを
意味しない。**git 依存の `prepare: tsc` がまだ走っている途中**で
`npm run typecheck` を叩くと、こう出る:

```
src/registry.ts(7,32): error TS2307: Cannot find module '@etzhayyim/sdk'
  or its corresponding type declarations.
src/registry.ts(126,14): error TS7006: Parameter 'r' implicitly has an 'any' type.
  … 以下 5 件同型
```

**これは repo の欠陥に見えるが、そうではない。** `@etzhayyim/sdk` の
`package.json` は `main`/`types`/`exports` が全部 `./dist/*` を指していて、
`dist/` は `prepare: tsc` が作る。install 途中なら当然まだ無い。
6 件の `TS7006` も派生（型が付かないので推論できないだけ）で、**原因は 1 つ**。

`npm install` の**プロセスが exit するのを待つ**こと。判定は
`ls node_modules/@etzhayyim/sdk/dist/index.d.ts` が在るかで取れる。

### A-2. typecheck

```bash
npm run typecheck        # tsc --noEmit
```

**期待**: 出力なし・**exit 0**（`strict: true` / `moduleResolution: bundler`）。

### A-3. test

```bash
npm test                 # vitest run
```

**期待**:

```
 Test Files  1 passed (1)
      Tests  4 passed (4)
```

exit 0。所要は **190–350 ms**（2 回の実測）。

### A-4. テストが本当に検査しているか確かめる（任意だが推奨）

**4 ケースしかないので、緑を信じる前に 1 度赤くしておく。** 下の 2 つは
実際に走らせて、**壊した不変条件と報告された失敗が一致すること**を確認した。

**壊し方 1 — 持分の上限（`src/types.ts`）**

```diff
-  return typeof n === "number" && Number.isInteger(n) && n >= 0 && n <= 1000;
+  return typeof n === "number" && Number.isInteger(n) && n >= 0 && n <= 2000;
```

```
FAIL  test/legal-entity.test.ts > … > records ownership edges (both FK),
      validates per-mille + self-loop
AssertionError: expected 'recorded' to be 'rejected'
Tests  1 failed | 3 passed (4)          exit 1
```

→ 千分率 1500 が通ってしまう。**落ちたのは per-mille のケース 1 件だけ。**

**壊し方 2 — 「解散は終端状態」（`src/registry.ts`）**

```diff
-  if (entity.status === "dissolved") return { status: "rejected", error: "entityDissolved" };
+  // guard removed
```

```
FAIL  test/legal-entity.test.ts > … > registers (jurisdiction/LEI validated),
      reads, lists, status lifecycle
AssertionError: expected 'updated' to be 'rejected'
Tests  1 failed | 3 passed (4)          exit 1
```

→ 解散済みの法人を `active` に戻せてしまう。**落ちたのは lifecycle のケース 1 件だけ。**

どちらも戻したあと `git diff --exit-code src/` が exit 0（バイト一致）で、
再実行して 4 passed に戻ることを確認している。

**この 4 ケースが触っていない経路**は README §6 に列挙した。

---

## B. `lg-clj/` — LangGraph サーバの Clojure 移植

### B-1. test

install の手順は要らない（`bb` が `bb.edn` の git 依存を自分で取る）。

```bash
cd lg-clj
bb test
```

**期待**:

```
Testing lg-legal-entity.smoke-test

Ran 15 tests containing 58 assertions.
0 failures, 0 errors.
```

exit 0。**外部ネットワークにも RisingWave にも触らない** —— collector の
handler は既定で「未配線」スタブを返す注入 seam になっている（README §1）。
初回は依存 2 本（`langchain-clj` / `langgraph-clj`）の取得で時間がかかる。

⚠ `bb`（babashka）は workspace 規約 ADR-2607173000 で script host としては
退役済み。**この `bb.edn` は既存物なのでそのまま使うが、新しいスクリプトを
`bb` で足さない**（README §3-D）。

### B-2. こちらも 1 度赤くしてある

**壊し方 — collector を 1 つ減らす（`src/lg_legal_entity/server.cljc`）**

```diff
-  ["Jpn" "Gbr" "Fra" "Nor" "Dnk" "Fin" "Est" "Cze" "Nzl" "Che" "Nld" "Isr"])
+  ["Jpn" "Gbr" "Fra" "Nor" "Dnk" "Fin" "Est" "Cze" "Nzl" "Che" "Nld"])
```

```
FAIL in (graphs-match-expected-set)   expected: (= 17 (count server/GRAPHS))
                                        actual: (not (= 17 16))
FAIL in (health-endpoint)             …registryCollectIsr が欠けた集合…
Ran 15 tests containing 56 assertions.
3 failures, 0 errors.                 exit 1
```

→ **3 つとも「`registryCollectIsr` が居ない」ことを報告している**（レジストリ
本体・health の返す一覧・件数）。壊したものと報告が一致する。戻して
58 assertions / 0 failures に復帰することを確認済み。

---

## C. `wasm/etzhayyim-wasm-legal-entity-le9k4x2m/` — **install できない**

```bash
cd wasm/etzhayyim-wasm-legal-entity-le9k4x2m
npm install
```

**実測（`--dry-run` でも同じ）**:

```
npm error code EUNSUPPORTEDPROTOCOL
npm error Unsupported URL Type "workspace:": workspace:*
```

exit 1。`"@etzhayyim/kotodama-host-sdk": "workspace:*"` を要求するのに、
**この repo にワークスペース根が無い**（リポジトリ直下に `package.json` も
`pnpm-workspace.yaml` も無い）。README §3-C。

**直さずに残してある。** `@etzhayyim/kotodama-host-sdk` の配布先を知っている
必要があるため。`svelte/` 側だけは通常の semver 依存なので、そこは単体で
install できる（このセッションでは実行していない ——
`svelte-kit sync` を伴うので副作用が大きい）。

---

## D. 後片付け

`.gitignore` が無いので、A-1 を踏むと**未追跡ファイルが 2 つ残る**:

```
?? kotoba/node_modules/
?? kotoba/package-lock.json
```

コミットしないこと（README §5 / §6）。消すなら:

```bash
rm -rf kotoba/node_modules kotoba/package-lock.json
```

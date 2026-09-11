(ns legal-entity.route
  "Which handler answers a request — as data, decided by a pure function.

  This is `.cljc` and not `.cljs` on purpose. Routing is the part of an edge
  worker that is worth testing, and it is testable here without a browser, a
  build, or a network. `legal-entity.worker` is the only namespace that touches
  Request/Response, and it does nothing this file has not already decided.

  It is also the first thing that should move to `.kotoba` once the ingress
  capability qualifies (`:native-aot`/`:wasm-aot` are pending today —
  ADR-2606290000): a route table is a decision over scalars and strings,
  which is exactly the shape that survives that move."
  (:require [kotoba.lang.text :as str]))

(def nsid-prefix
  "legalEntity の XRPC 名前空間。移行前は 2 箇所が別々に宣言していた:
  `kotodama.jsonld` の `triggers.subscribeRepos.collections`（8 本の
  `com.etzhayyim.legalEntity.*`）と、撤去した `src/app.ts` の `NSID_PREFIX`。

  **この prefix は表示のためだけに使う** —— 中継そのものは deploy されていた
  SvelteKit の route と同じく prefix を検査しない。"
  "com.etzhayyim.legalEntity.")

(def actor-did
  "`kotodama.jsonld` の `@id`。ここに写しているのは `/health` が名乗るためで、
  正本はあちら側。DID document は取得できない —— `did:web` の解決には
  `https://legal-entity.etzhayyim.com/.well-known/did.json` が要るが、そのホストは
  NXDOMAIN である（README §3-A の DNS 表）。"
  "did:web:legal-entity.etzhayyim.com")

(def default-nanoid
  "撤去した `src/app.ts` が `env.APP_NANOID ?? \"le9k4x2m\"` と書いていた既定値。
  `wrangler.jsonc` の `APP_NANOID` と同じ値である。"
  "le9k4x2m")

(def collections
  "`kotodama.jsonld` の `triggers.subscribeRepos.collections`。**この repo で
  唯一の実在する NSID 一覧**であり、`APP_CAPABILITIES` とは別物である
  （下の `capability-labels` の docstring を読むこと）。

  ページはこれを『この actor が purchase する collection』として描く。
  中継の許可リストではない —— 中継は prefix を検査しない。"
  ["com.etzhayyim.legalEntity.legalEntity"
   "com.etzhayyim.legalEntity.companyFiling"
   "com.etzhayyim.legalEntity.companyFact"
   "com.etzhayyim.legalEntity.entityOwnership"
   "com.etzhayyim.legalEntity.entityTradeRelationship"
   "com.etzhayyim.legalEntity.publicStatement"
   "com.etzhayyim.legalEntity.entityMention"
   "com.etzhayyim.legalEntity.entityRelation"])

(def routes
  "The public surface, as data. The landing page renders THIS, so a route that
  exists and a route the page advertises cannot drift apart — the defect
  docs/adr/0001 records was a page that said `routeCount: 0`, `routes: []` and
  `vars: []` beside a wrangler.jsonc declaring one route and nine vars."
  [{:route/path "/"          :route/method :get  :route/kind :page
    :route/doc "この appview の説明ページ"}
   {:route/path "/health"    :route/method :get  :route/kind :json
    :route/doc "生存確認。デプロイされた面が答えることを外から確かめられる"}
   {:route/path "/xrpc/:nsid" :route/method :post :route/kind :proxy
    :route/doc "XRPC を MCP router へ中継する"}])

(defn- xrpc-rest
  "`/xrpc/<rest>` の rest。無ければ nil。

  **多段パスを 400 にしない。** deploy されていた SvelteKit の route は
  `[...path]`（rest parameter）なので `/xrpc/a/b` は nsid `\"a/b\"` として
  上流へ渡っていた。移行はその意味論を変えない —— 変えるなら移行ではなく
  別の決定である。空（`/xrpc` と `/xrpc/`）だけが 400
  （`Missing XRPC method`、文言も SvelteKit 版そのまま）。"
  [path]
  (when (str/starts-with? path "/xrpc/")
    (let [rest' (subs path (count "/xrpc/"))]
      (when (seq rest') rest'))))

(defn dispatch
  "method + path → 何をするか。Request も Response も知らない。

  返すのは `{:action …}` で、`:action` は
  `:page` / `:health` / `:xrpc` / `:cors-preflight` / `:not-found` /
  `:method-not-allowed` / `:bad-request` のいずれか。"
  [method path]
  (let [m (keyword (str/lower (or method "get")))
        p (or path "")]
    (cond
      (and (= m :options) (or (= p "/xrpc") (str/starts-with? p "/xrpc/")))
      {:action :cors-preflight}

      (or (= p "/xrpc") (str/starts-with? p "/xrpc/"))
      (if (= m :post)
        (if-let [nsid (xrpc-rest p)]
          {:action :xrpc :nsid nsid}
          {:action :bad-request :reason "Missing XRPC method"})
        {:action :method-not-allowed :allow "POST, OPTIONS"})

      (= p "/health") (if (= m :get)
                        {:action :health}
                        {:action :method-not-allowed :allow "GET"})
      (= p "/")       (if (= m :get)
                        {:action :page}
                        {:action :method-not-allowed :allow "GET"})
      :else {:action :not-found})))

(defn mcp-router-url
  "env の設定 → MCP router の URL。末尾スラッシュは落とす。

  解決順（`AGENTGATEWAY_MCP_ROUTER_URL` → `MCP_ROUTER_URL` → 既定）も、
  空白だけを未設定として扱うところも、deploy されていた
  `svelte/src/routes/xrpc/[...path]/+server.ts` の `mcpRouterUrl` と同じ。"
  [{:keys [AGENTGATEWAY_MCP_ROUTER_URL MCP_ROUTER_URL]}]
  (let [pick (fn [s] (when (and (string? s) (seq (str/trim s))) (str/trim s)))]
    (-> (or (pick AGENTGATEWAY_MCP_ROUTER_URL)
            (pick MCP_ROUTER_URL)
            "https://mcp.etzhayyim.com/xrpc/com.etzhayyim.mcp.message")
        (str/replace #"/+$" ""))))

(defn capability-labels
  "`APP_CAPABILITIES` が並べる文字列を、**そのまま**整えて返す（prefix を足さない）。

  ⚠ **ここは同型移行（app-ongakuka / app-air-cargo 等）と意図的に違う。**
  あちらは `capability-nsids` という名前で、`APP_CAPABILITIES` の各要素に
  `nsid-prefix` を足して完全修飾 NSID にしてページへ印字している。**この repo で
  同じことをすると嘘になる。** 根拠は 3 つとも実測:

  1. この repo の `APP_CAPABILITIES` は
     `[\"gleif-lei-ingest\" \"corporate-registry\" \"legal-entity-search\"
       \"multi-country-ingest\" \"entity-resolution\"]` で、**`kotodama.jsonld` の
     `profile.capabilities` と 1 バイト違わず同一**である。あちらは
     `profile`（名乗り）の下にあり、メソッド一覧ではない。
  2. **deploy されていたコードは `APP_CAPABILITIES` を一度も読んでいない。**
     `wasm/` 以下でこの名前が出るのは `wrangler.jsonc` の宣言 1 箇所だけで、
     `src/app.ts` にも SvelteKit の route にも参照が無い。prefix を足す実装は、
     どのコードも主張していなかった対応関係を発明することになる。
  3. この repo に実在する NSID は `kotodama.jsonld` の
     `triggers.subscribeRepos.collections` の 8 本（= `collections`）で、
     **5 つの capability 文字列はそのどれの末尾セグメントでもない**。

  （NSID の綴り自体は判断の根拠にしていない。この workspace 自身の
  `atproto.core/collection?` は `^[a-zA-Z][a-zA-Z0-9-]*(\\.[a-zA-Z0-9-]+)+$` で
  ハイフンを許すので、`com.etzhayyim.legalEntity.gleif-lei-ingest` は綴りとしては
  通ってしまう。**通る綴りであることは、それが実在するメソッドであることの
  証拠ではない。** 上の 3 点はどれも綴りに依存しない。）

  ページは capability を「宣言されている能力ラベル」として、`collections` を
  「purchase する collection の NSID」として、**別々の表**に描く。"
  [names]
  (into []
        (comp (filter string?)
              (map str/trim)
              (remove str/blank?))
        names))

(def ^:private drop-headers
  "上流へ渡さない header。

  `host` —— 移行前の SvelteKit route も削っていた（宛先が変わるので嘘になる）。
  移行はここまでは正しく写していた。

  `content-length` / `content-encoding` —— **これが抜けていた。** body は
  JSON-RPC の封筒に詰め直されるので、呼び手が付けた長さもエンコーディングも
  もう本文を説明していない。それを載せたまま上流へ投げると fetch 自体が失敗し、
  Worker は 502 `MCP router unreachable` を返す —— router には 1 度も届かない。
  実測 2026-08-19、ビルド済み bundle に `content-length` 付きの POST を通して
  確認した（付けなければ同じ bundle が 200 を返す）。POST に `content-length`
  を付けないクライアントは実際にはほぼ無いので、これは稀な経路ではない。

  **それ以外は全部渡す。** `authorization` はこの repo では最初から届いていた。"
  #{"host" "content-length" "content-encoding"})

(defn relay-headers
  "受け取った header を、上流へ渡す形にする。`in` は [[k v] …] の列。

  ここが `.cljc` にあるのは、これがビルドもブラウザも無しに固定できる**判断**
  だからである。`js/Headers` を worker 側で組み立てていたので、何が渡って何が
  落ちるかを述べたテストが書けず、上の欠陥は誰にも気づかれなかった。

  `x-etzhayyim-bff` の値だけは移行で変えてある（SvelteKit 版は
  `sveltekit-edge-bff` を名乗っていた）。名乗りは事実なので、SvelteKit で
  なくなった後もそう名乗り続けるのは嘘になる（APP_FRAMEWORK も同時に変えた）。
  この註は worker 側の組み立てに付いていたもので、値と一緒にここへ移した。"
  [in nsid]
  (into {"content-type" "application/json"
         "x-etzhayyim-bff" "cljs-worker"
         "x-etzhayyim-xrpc-method" nsid}
        (comp (remove (fn [[k _]] (contains? drop-headers (str/lower k))))
              (map (fn [[k v]] [(str/lower k) v])))
        in))

(defn unwrap-mcp
  "MCP router の応答から、呼び手に返す値を取り出す。

  `{:result {:structuredContent X}}` → X、`{:result X}` → X、それ以外は素通し。
  `{:error …}` は呼び出し側が 502 にするので、ここでは判定だけ返す。
  `nil`（上流が空 body）は `{}` —— SvelteKit 版の `structured ?? {}` と同じ。"
  [payload]
  (cond
    (and (map? payload) (contains? payload :error))
    {:ok? false :error (get-in payload [:error :message] "MCP router returned an error")
     :upstream payload}

    (and (map? payload) (contains? payload :result))
    (let [r (:result payload)
          v (if (and (map? r) (contains? r :structuredContent))
              (:structuredContent r)
              r)]
      {:ok? true :value (if (nil? v) {} v)})

    :else {:ok? true :value (if (nil? payload) {} payload)}))

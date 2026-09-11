(ns legal-entity.route-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.lang.text :as str]
            [legal-entity.route :as route]
            [legal-entity.view :as view]))

(deftest dispatch-page-and-health
  (is (= :page (:action (route/dispatch "GET" "/"))))
  (is (= :health (:action (route/dispatch "GET" "/health"))))
  (is (= :method-not-allowed (:action (route/dispatch "POST" "/health"))))
  (is (= :not-found (:action (route/dispatch "GET" "/nope"))))
  (testing "撤去した src/app.ts の別名 3 本は持ち越していない
            (/healthz /readyz /_app/meta。あれは deploy されていなかった)"
    (is (= :not-found (:action (route/dispatch "GET" "/healthz"))))
    (is (= :not-found (:action (route/dispatch "GET" "/readyz"))))
    (is (= :not-found (:action (route/dispatch "GET" "/_app/meta"))))))

(deftest dispatch-xrpc
  (testing "nsid はそのまま渡す"
    (is (= {:action :xrpc :nsid "com.etzhayyim.legalEntity.legalEntity"}
           (route/dispatch "POST" "/xrpc/com.etzhayyim.legalEntity.legalEntity"))))
  (testing "多段パスも nsid として通す —— deploy されていた SvelteKit の
            rest parameter [...path] と同じ意味論。移行はここを変えない"
    (is (= {:action :xrpc :nsid "a/b"} (route/dispatch "POST" "/xrpc/a/b"))))
  (testing "prefix の検査はしない。deploy されていた route も検査していない
            （NSID_PREFIX を見ていたのは deploy されていない src/app.ts の方）"
    (is (= {:action :xrpc :nsid "com.example.other"}
           (route/dispatch "POST" "/xrpc/com.example.other"))))
  (testing "空だけが 400。文言も SvelteKit 版のまま"
    (is (= {:action :bad-request :reason "Missing XRPC method"}
           (route/dispatch "POST" "/xrpc/")))
    (is (= :bad-request (:action (route/dispatch "POST" "/xrpc")))))
  (testing "preflight と method"
    (is (= :cors-preflight (:action (route/dispatch "OPTIONS" "/xrpc/x"))))
    (is (= :cors-preflight (:action (route/dispatch "OPTIONS" "/xrpc"))))
    (is (= {:action :method-not-allowed :allow "POST, OPTIONS"}
           (route/dispatch "GET" "/xrpc/x")))))

(deftest mcp-url-resolution
  (is (= "https://mcp.etzhayyim.com/xrpc/com.etzhayyim.mcp.message"
         (route/mcp-router-url {})))
  (is (= "https://a.example/x" (route/mcp-router-url {:AGENTGATEWAY_MCP_ROUTER_URL "https://a.example/x/"})))
  (testing "空白だけの設定は未設定として扱う"
    (is (= "https://b.example" (route/mcp-router-url {:AGENTGATEWAY_MCP_ROUTER_URL "   "
                                                      :MCP_ROUTER_URL "https://b.example"})))))

(deftest capability-labels-are-not-nsids
  (testing "APP_CAPABILITIES の 5 本は **そのまま** 出る。prefix を足さない ——
            この repo の値は kebab-case の能力ラベルであってメソッド名ではない
            （同型移行の capability-nsids と意図的に違う。docs/adr/0001）"
    (is (= ["gleif-lei-ingest" "corporate-registry" "legal-entity-search"
            "multi-country-ingest" "entity-resolution"]
           (route/capability-labels
            ["gleif-lei-ingest" "corporate-registry" "legal-entity-search"
             "multi-country-ingest" "entity-resolution"]))))
  (testing "prefix を足していないことを名指しで固定する。
            足す実装に戻ったらここが落ちる"
    (is (every? #(not (str/starts-with? % route/nsid-prefix))
                (route/capability-labels ["gleif-lei-ingest" "entity-resolution"]))))
  (testing "空・非文字列は落とす（宣言が壊れていてもページは描ける）"
    (is (= [] (route/capability-labels ["" "   " nil 42])))))

(deftest collections-are-the-real-nsids
  (testing "kotodama.jsonld の 8 本。capability ラベルとは重ならない"
    (is (= 8 (count route/collections)))
    (is (every? #(str/starts-with? % route/nsid-prefix) route/collections))
    (is (empty? (filter (set route/collections)
                        ["gleif-lei-ingest" "corporate-registry" "legal-entity-search"
                         "multi-country-ingest" "entity-resolution"])))))

(deftest unwrap
  (is (= {:ok? true :value {:a 1}} (route/unwrap-mcp {:result {:structuredContent {:a 1}}})))
  (is (= {:ok? true :value {:a 1}} (route/unwrap-mcp {:result {:a 1}})))
  (testing "上流が空 body なら {} —— SvelteKit 版の `structured ?? {}`"
    (is (= {:ok? true :value {}} (route/unwrap-mcp nil))))
  (is (false? (:ok? (route/unwrap-mcp {:error {:message "boom"}})))))

(deftest page-shows-the-real-data
  (testing "ページは渡された値から描く。0 も [] も焼かない（docs/adr/0001 の欠陥）"
    (let [caps (route/capability-labels
                ["gleif-lei-ingest" "corporate-registry" "legal-entity-search"
                 "multi-country-ingest" "entity-resolution"])
          html (view/render {:css "/*x*/" :routes route/routes
                             :capabilities caps
                             :collections route/collections
                             :vars [:APP_NANOID :APP_UI_TYPE]
                             :mcp-url "https://mcp.example/x"
                             :actor route/actor-did})]
      (doseq [r route/routes]
        (is (str/includes? html (:route/path r))
            (str (:route/path r) " がページに出ていない")))
      (doseq [c caps]
        (is (str/includes? html c) (str c " がページに出ていない")))
      (doseq [c route/collections]
        (is (str/includes? html c) (str c " がページに出ていない")))
      (is (str/includes? html "APP_NANOID"))
      (is (str/includes? html "https://mcp.example/x"))
      (is (str/includes? html route/actor-did))
      (testing "移行前のページが出していた嘘の文言は無い"
        (is (not (str/includes? html "No public route is declared")))
        (is (not (str/includes? html "No public vars are declared")))))))

(deftest page-shows-what-it-is-given-not-a-baked-table
  (testing "同じ view に別の値を渡せば別の結果になる。ページが引数を無視して
            自前の定数を描いていたら、この 2 つは区別できない"
    (let [a (view/render {:css "" :routes [{:route/path "/only-a" :route/method :get
                                            :route/kind :page :route/doc "a"}]
                          :capabilities ["cap-a"] :collections ["com.example.a"]
                          :vars [:VAR_A]
                          :mcp-url "https://a.invalid/x" :actor "did:web:a.invalid"})
          b (view/render {:css "" :routes [{:route/path "/only-b" :route/method :post
                                            :route/kind :page :route/doc "b"}]
                          :capabilities ["cap-b"] :collections ["com.example.b"]
                          :vars [:VAR_B]
                          :mcp-url "https://b.invalid/x" :actor "did:web:b.invalid"})]
      (doseq [[html present absent]
              [[a "/only-a" "/only-b"] [b "/only-b" "/only-a"]
               [a "cap-a" "cap-b"] [b "cap-b" "cap-a"]
               [a "com.example.a" "com.example.b"] [b "com.example.b" "com.example.a"]
               [a "VAR_A" "VAR_B"] [b "VAR_B" "VAR_A"]
               [a "a.invalid" "b.invalid"] [b "b.invalid" "a.invalid"]]]
        (is (str/includes? html present) (str present " が出ていない"))
        (is (not (str/includes? html absent)) (str absent " が漏れている"))))))

(deftest relay-headers-forwards-what-it-received
  (testing "移行前から authorization は届いていた。落ちていたのは長さの方"
    (let [h (route/relay-headers [["Host" "x.example"]
                                  ["Authorization" "Bearer t"]
                                  ["Content-Length" "9"]
                                  ["Content-Encoding" "gzip"]
                                  ["X-Trace" "abc"]]
                                 "com.a.b")]
      (is (= "Bearer t" (get h "authorization"))
          "authorization が落ちている —— preflight はこれを許可すると言っている")
      (is (= "abc" (get h "x-trace"))
          "呼び手が付けた header が落ちている")
      (is (nil? (get h "host")) "host は宛先が変わるので渡さない")
      (is (nil? (get h "content-length"))
          "呼び手の長さを載せると上流への fetch が失敗し、502 になる（実測）")
      (is (nil? (get h "content-encoding"))
          "body を詰め直すので元の encoding も嘘になる")
      (is (= "application/json" (get h "content-type")))
      (is (= "com.a.b" (get h "x-etzhayyim-xrpc-method")))
      (is (= "cljs-worker" (get h "x-etzhayyim-bff"))))))

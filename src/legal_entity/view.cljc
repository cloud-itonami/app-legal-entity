(ns legal-entity.view
  "この appview の説明ページ。純 hiccup。

  基盤は `jp-go-dds`(デジタル庁デザインシステム) —— superproject の
  skill `kotoba-uiux` が定める新規 UI の base。色・寸法は `--hig-*` トークン
  契約で書き、raw hex も px フォントサイズも置かない。

  **表示する事実は引数で受け取る。ページの中に焼かない。**
  これは装飾の都合ではなく、docs/adr/0001 が記録した欠陥そのものへの答えで
  ある —— 移行前の `+page.svelte` は `routeCount: 0` / `routes: []` /
  `vars: []` を literal で持っていて、隣の `wrangler.jsonc` が route 1・var 9・
  capability 5 を宣言していることに気づけなかった。ここでは route 表も env の
  キーも capability も渡す側が持ち、ページは描くだけなので、両者がずれる余地が
  無い。"
  (:require [jp-go-dds.core :as dds]
            [jp-go-dds.page :as page]
            [jp-go-dds.tokens :as tokens]
            [kotoba.lang.text :as str]))

(def app-css
  "app 固有の最小 CSS。`--hig-*` 契約だけを使う(bridge が DADS の上に再定義する)。
  DADS を base にした app の下には `shitsuke.hig` が居ないので、bridge が運んで
  いないトークンは何にも解決しない —— 使うのは運ばれている中だけ。"
  (str/join
   "\n"
   [".le-lede { color: var(--hig-color-secondary-label); max-width: 42rem; }"
    ".le-note { color: var(--hig-color-secondary-label); font-size: var(--hig-text-footnote-font-size); }"
    ".le-mono { font-family: var(--hig-font-mono); }"]))

(defn- route-rows [routes]
  (mapv (fn [r]
          [(str/upper (name (:route/method r)))
           [:span {:class "le-mono"} (:route/path r)]
           (:route/doc r)])
        routes))

(defn body
  "opts:
   :routes       legal-entity.route/routes（この Worker が実際に答えるもの）
   :capabilities `APP_CAPABILITIES` が並べる能力ラベル（**NSID ではない**）
   :collections  `kotodama.jsonld` が purchase する collection の NSID
   :vars         wrangler が渡した env のキー（値は出さない）
   :mcp-url      XRPC の中継先（route/mcp-router-url の戻り値）
   :actor        この appview の actor DID
   :built-at     bundle のビルド時刻（不明なら nil）"
  [{:keys [routes capabilities collections vars mcp-url actor built-at]}]
  (dds/container
   (dds/section
    {}
    (dds/heading 1 "etzhayyim Legal Entity Collector")
    [:p {:class "le-lede"}
     "世界各国の法人登記情報（GLEIF LEI・各国レジストリ・SEC disclosure）を"
     "収集する actor の公開面。収集そのものは MCP router の先（AgentGateway / "
     "pod 側 LangServer）にあり、ここには無い —— この面は薄い edge であって、"
     "実装ではない。"])

   (dds/section
    {:title "この面が答えるもの"}
    (dds/table {:caption "公開ルート"
                :headers ["METHOD" "PATH" "何をするか"]
                :rows (route-rows routes)})
    [:p {:class "le-note"}
     "この表は Worker の route 表そのものから描いている。ページに焼いた値では"
     "ないので、実際に答えるものと表示がずれない。"])

   (dds/section
    {:title "宣言されている能力"}
    (if (seq capabilities)
      [:div
       [:p (into [:span] (interpose " " (map (fn [c] (dds/chip-label c)) capabilities)))]
       [:p {:class "le-note"}
        "wrangler の "
        [:span {:class "le-mono"} "APP_CAPABILITIES"]
        " を読んで描いている。**これは XRPC のメソッド名ではない** —— "
        [:span {:class "le-mono"} "kotodama.jsonld"]
        " の profile.capabilities と同じ能力ラベルで、NSID prefix は付けない"
        "（同型移行の他 repo は付けているが、あちらの値は camelCase の"
        "メソッド名である。docs/adr/0001）。"]]
      [:p {:class "le-note"} "APP_CAPABILITIES が渡されていない（ローカル描画）。"]))

   (dds/section
    {:title "purchase する collection"}
    (if (seq collections)
      [:div
       (dds/table {:caption "kotodama.jsonld の triggers.subscribeRepos.collections"
                   :headers ["NSID"]
                   :rows (mapv (fn [c] [[:span {:class "le-mono"} c]]) collections)})
       [:p {:class "le-note"}
        "この repo で実在が確かめられる唯一の NSID 一覧。中継そのものは prefix を"
        "検査しない（deploy されていた SvelteKit の route と同じ）ので、この表は"
        "「宣言」であって「許可リスト」ではない。"]]
      [:p {:class "le-note"} "collection が渡されていない（ローカル描画）。"]))

   (dds/section
    {:title "実行時の設定"}
    (if (seq vars)
      [:div (into [:p] (interpose " "
                                  (map (fn [k] (dds/chip-label (name k))) vars)))
       [:p {:class "le-note"} "キー名のみ。値は出さない —— **下の中継先を除いて**。"
        "どこへ中継するかは運用者が見る必要があるので意図的に出している。"]]
      [:p {:class "le-note"} "env が渡されていない（ローカル描画）。"])
    [:p {:class "le-note"} "XRPC の中継先: "
     [:span {:class "le-mono"} mcp-url]]
    (when actor
      [:p {:class "le-note"} "actor: " [:span {:class "le-mono"} actor]]))

   (dds/section
    {:title "現在地"}
    [:p {:class "le-lede"}
     "この appview は TypeScript/Svelte から ClojureScript へ移行済み。"
     "deploy される bundle は、いま読んでいるソースからコンパイルされたもので"
     "ある（docs/adr/0001）。"
     "なお同じ repo に在る "
     [:span {:class "le-mono"} "kotoba/"]
     "（TypeScript の登記レジストリ）と "
     [:span {:class "le-mono"} "lg-clj/"]
     "（LangGraph の Clojure 移植）は appview ではなく、この移行の対象外である。"]
    (when built-at
      [:p {:class "le-note"} "bundle build: " built-at]))))

(defn render
  "完全な HTML 文書。`css` は呼び出し側が渡す(ライブラリは I/O を持たない)。"
  [{:keys [css] :as opts}]
  (page/->page
   {:title "etzhayyim Legal Entity Collector"
    :description "世界各国の法人登記情報を収集する actor の appview 公開面。収集は MCP router の先にある。"
    :lang "ja"
    :css css
    :app-css (str tokens/bridge-css "\n" app-css)}
   (body opts)))

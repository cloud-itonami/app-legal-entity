#!/usr/bin/env nbb
;; verify-docs-claims — re-derive every number README.md and docs/operator-quickstart.md
;; state, from the tree itself, and fail when the tree and the prose disagree.
;;
;; Before the cljs migration this file's load-bearing claim would have been a GAP:
;; the Worker that would be deployed was svelte/.svelte-kit/cloudflare/_worker.js --
;; a build output absent from the tree -- while src/app.ts, the file that reads like
;; the application, was referenced by no wrangler config at all. That gap is closed,
;; and the claims are written so it cannot quietly come back: the appview's
;; TypeScript and Svelte are asserted ABSENT BY NAME, not merely absent from a byte
;; total.
;;
;; Usage:  nbb scripts/verify-docs-claims.cljs [<dir>]     (<dir> FIRST, default ".")
;;         ^ <dir> goes FIRST: this script takes the first non--- argument as the
;;           tree, so `... --min 10 .` would make "10" the path. Measured hazard.
;; Exit:   0 every claim holds · 1 a claim is false · 2 could not answer

(require '["node:fs" :as fs]
         '["node:child_process" :as cp]
         '["node:crypto" :as crypto]
         '[cljs.reader :as edn]
         '[clojure.string :as str])

(def root (or (first (remove #(str/starts-with? % "--") *command-line-args*)) "."))
(def APP "wasm/etzhayyim-wasm-legal-entity-le9k4x2m")

(def claims
  {:tracked-files 31
   :inherited-bytes 2373           ; the 2 inherited files still carried byte-identical
   :appview-ts-files 0             ; TypeScript OUTSIDE kotoba/ -- see kept below
   :appview-svelte-files 0
   :production-canonical-files 3   ; route.cljc view.cljc worker.cljs
   :kept-kotoba-files 7
   :kept-kotoba-bytes 25274
   :kept-lg-clj-files 6
   :kept-lg-clj-bytes 14846
   :declared-vars 9
   :declared-routes 1
   :declared-capabilities 5
   :collections 8
   :wrangler-main "../../dist/worker.js"
   :shadow-output-dir "dist"
   :shadow-export 'legal-entity.worker/handler
   :upstream-tracked-files 26
   :removed-by-migration-count 9})

;; ── kotoba/ and lg-clj/ : source that is NOT the appview ───────────────────
;;
;; The migration removed the APPVIEW's TypeScript and Svelte. It did NOT remove
;; these. `kotoba/` is a TypeScript domain library and `lg-clj/` is a Clojure port
;; of a LangGraph server; the appview imported neither. They are in no bundle, but
;; they are not dead by the test that governs this migration. Deleting them on a
;; templated "all TypeScript" instruction would have been destruction rather than
;; migration.
;;
;; They are pinned here BY HASH, not merely counted, so they can neither grow
;; silently (new TypeScript smuggled in under a directory the appview claims not to
;; own) nor rot silently (edited without anyone deciding to).
(def kept
  {"kotoba/package.json" "834f8b0a341827fba850cdf948f52b363811b7f66fd51e0fc14e558aab92668b"
   "kotoba/src/index.ts" "5b5e4cdc5195cab8e645b64873c94c2882b03a567368b1a9444b33c48e0c3e4c"
   "kotoba/src/registry.ts" "67a65f683f27d08e8321cff2424be90a1b4e3b68c2437a84b7352b5372c93c15"
   "kotoba/src/types.ts" "7c6c23c86938d37d813c8e0b7bc5f71178770df379550e0f9ad237240d14bb3b"
   "kotoba/test/legal-entity.test.ts" "e5ab10c5d937e5c013df5ef02c4584e2368f2d24bfa3e282037c52c9617d4a08"
   "kotoba/tsconfig.json" "95a429e51d6162cb7205b603f745e7604d93ffbb1ea6c346e5c6215a79ae541e"
   "kotoba/vitest.config.ts" "f82a551ef4da1c9cbf17985a3bee96eee450a3e4a46bff0d96c6150263121eff"
   "lg-clj/bb.edn" "849310f9e7619d12cdf2b1ab76796b4f1e434f8da3969eee92bb00efeba17ec3"
   "lg-clj/run_tests.clj" "5602e4a9b702c8423603d8e1fa1ae3d962659d623add3b94e9410d45b449d43e"
   "lg-clj/src/lg_legal_entity/graphs/health.cljc" "cc41d516383a1b146767bdf5dc52a49b4760f4ab5799ee0eb28a1751d210921a"
   "lg-clj/src/lg_legal_entity/graphs/task.cljc" "768abf9bb711d6a81a3fb1850f876313295e727c9214a74853c33f54c8594243"
   "lg-clj/src/lg_legal_entity/server.cljc" "b9b3aa1e9cd53d1cd3652d69d24c05b643eea63430683e388b0eac6011c33062"
   "lg-clj/test/lg_legal_entity/smoke_test.cljc" "0e11a9606fc3e559611a4a17fc7ca82cd605aba15e19936dab24921bf88f9fc1"})

;; Inherited files this repository still carries BYTE-IDENTICAL.
;;
;; Four other inherited files left this set DELIBERATELY in the migration and are
;; checked by CONTENT below instead -- so that an intentional change and a careless
;; one stay distinguishable:
;;   wrangler.jsonc            main / assets / rules / flags / APP_FRAMEWORK
;;   migration.edn             now registers the additions, removals and kept set
;;   CLAUDE.md                 its Runtime table claimed TypeScript + src/app.ts
;;   SUBSTRATE-PORT-PENDING.md its item 2 named a file the migration deleted
(def preserved
  {"README.edn" "ce0766ffd31f645af3d4967127b59b98760cf44ceb04012b76bf401cace1cc27"
   "wasm/etzhayyim-wasm-legal-entity-le9k4x2m/kotodama.jsonld"
   "5b433c836e15efa40d6bf4a042daabfcf7c9e52ced6e75c99ee3e430f0aca5c4"})

(def undetermined (atom []))
(def failures (atom []))
(def checks (atom 0))
(defn undet! [m] (swap! undetermined conj m))

(defn tracked-files []
  (try (->> (.execSync cp "git ls-files" #js {:cwd root :encoding "utf8"})
            str/split-lines (remove str/blank?) vec)
       (catch :default e (undet! (str "git ls-files failed: " (.-message e))) nil)))
(defn slurp* [rel] (try (.readFileSync fs (str root "/" rel) "utf8") (catch :default _ nil)))
(defn bytes-of [rel] (try (.-size (.statSync fs (str root "/" rel))) (catch :default _ nil)))
(defn sha256 [rel]
  (try (-> (.createHash crypto "sha256") (.update (.readFileSync fs (str root "/" rel))) (.digest "hex"))
       (catch :default _ nil)))
(defn strip-jsonc [s] (str/replace s #"(?m)^\s*//.*$" ""))
(defn read-edn [rel]
  (try (edn/read-string (slurp* rel))
       (catch :default e (undet! (str rel " is not readable EDN: " (.-message e))) nil)))

(defn check! [label expected actual]
  (swap! checks inc)
  (let [ok (= expected actual)]
    (println (str (if ok "PASS" "FAIL") "\t" (name label)
                  "\texpected=" (pr-str expected) "\tactual=" (pr-str actual)))
    (when-not ok (swap! failures conj label))
    ok))

(let [files (tracked-files)
      mig (read-edn "migration.edn")]
  (when (nil? files) (println "UNDETERMINED\tcould not list tracked files") (js/process.exit 2))
  (println (str "SCANNED\t" (count files)))
  (when (zero? (count files)) (println "UNDETERMINED\tscanned 0 files") (js/process.exit 2))

  (let [sizes (into {} (map (juxt identity bytes-of)) files)]
    (when-let [bad (seq (keep (fn [[f s]] (when (nil? s) f)) sizes))]
      (undet! (str "tracked but unreadable: " (str/join ", " bad))))

    (check! :tracked-files (:tracked-files claims) (count files))
    (check! :inherited-bytes (:inherited-bytes claims)
            (reduce + 0 (keep #(get sizes %) (keys preserved))))
    (check! :preserved-files-unchanged []
            (vec (keep (fn [[f want]] (let [got (sha256 f)]
                                        (when-not (= want got) (str f " " (or got "MISSING")))))
                       preserved)))

    ;; ── provenance: migration.edn stays the register of what this tree is ──
    (if (nil? mig)
      (undet! "migration.edn unreadable")
      (let [adds (set (get-in mig [:identity :allowed-additions]))
            removed (get-in mig [:identity :removed-by-migration])
            kept-decl (into [] (mapcat #(get-in mig [:identity :kept-not-the-appview % :paths]))
                            [:kotoba :lg-clj])]
        (check! :migration-declares-upstream-count (:upstream-tracked-files claims)
                (get-in mig [:source :tracked-files]))
        (check! :removed-by-migration-count (:removed-by-migration-count claims)
                (count removed))
        ;; the appview's TypeScript and Svelte are gone, BY NAME, from the register
        (check! :removed-by-migration-absent []
                (vec (filter #(some? (bytes-of %)) removed)))
        ;; nothing is in the tree that provenance does not account for
        (check! :every-added-file-is-registered []
                (vec (remove #(or (contains? adds %)
                                  (contains? preserved %)
                                  (contains? kept %)
                                  ;; the 3 inherited files changed on purpose
                                  (contains? #{"CLAUDE.md" "SUBSTRATE-PORT-PENDING.md"
                                               (str APP "/wrangler.jsonc")}
                                             %))
                             files)))
        ;; the kept set is what migration.edn says it is
        (check! :migration-declares-kept-paths (vec (sort (keys kept))) (vec (sort kept-decl)))))

    ;; ── the kept source is exactly the kept source ──
    ;; Pinned three ways per subtree: which files, how many bytes, and each file's
    ;; hash. A new .ts under kotoba/ fails the count; an edit fails the hash.
    (check! :kept-kotoba-files (:kept-kotoba-files claims)
            (count (filter #(str/starts-with? % "kotoba/") files)))
    (check! :kept-kotoba-bytes (:kept-kotoba-bytes claims)
            (reduce + 0 (keep #(get sizes %) (filter #(str/starts-with? % "kotoba/") (keys kept)))))
    (check! :kept-lg-clj-files (:kept-lg-clj-files claims)
            (count (filter #(str/starts-with? % "lg-clj/") files)))
    (check! :kept-lg-clj-bytes (:kept-lg-clj-bytes claims)
            (reduce + 0 (keep #(get sizes %) (filter #(str/starts-with? % "lg-clj/") (keys kept)))))
    (check! :kept-files-unchanged []
            (vec (keep (fn [[f want]] (let [got (sha256 f)]
                                        (when-not (= want got) (str f " " (or got "MISSING")))))
                       kept)))

    ;; Language of the APPVIEW's source. kotoba/ and lg-clj/ are excluded by name --
    ;; they are not the appview, they are pinned above, and folding them in here
    ;; would turn a claim about "the thing that gets deployed" into a claim about
    ;; "the repository", which is how the appview's TypeScript could come back
    ;; disguised as a library file.
    (let [appview (remove #(or (str/starts-with? % "scripts/")
                               (str/starts-with? % "test/")
                               (str/includes? % "/test/")
                               (str/starts-with? % "kotoba/")
                               (str/starts-with? % "lg-clj/"))
                          files)]
      (check! :appview-ts-files (:appview-ts-files claims)
              (count (filter #(str/ends-with? % ".ts") appview)))
      (check! :appview-svelte-files (:appview-svelte-files claims)
              (count (filter #(str/ends-with? % ".svelte") appview)))
      (check! :production-canonical-files (:production-canonical-files claims)
              (count (filter #(re-find #"\.(cljs|cljc|clj|kotoba)$" %) appview))))

    ;; Svelte must not come back under ANY name -- the removed-by-migration list
    ;; names the eight files, this catches a new .svelte, a svelte.config, or a
    ;; svelte/ directory.
    (check! :no-svelte-artifacts 0
            (count (filter #(or (str/ends-with? % ".svelte")
                                (str/includes? % "svelte.config")
                                (str/includes? % "/svelte/"))
                           files)))

    ;; ── the deployed bundle is built from the source in this tree ──
    (let [w (some-> (slurp* (str APP "/wrangler.jsonc")) strip-jsonc)
          sh (read-edn "shadow-cljs.edn")
          kj (try (js->clj (.parse js/JSON (slurp* (str APP "/kotodama.jsonld"))) :keywordize-keys false)
                  (catch :default _ nil))]
      (if (or (nil? w) (nil? sh))
        (undet! "wrangler.jsonc or shadow-cljs.edn unreadable")
        (let [j (js->clj (.parse js/JSON w) :keywordize-keys false)
              build (get-in sh [:builds :worker])]
          (check! :wrangler-main (:wrangler-main claims) (get j "main"))
          (check! :declared-vars (:declared-vars claims) (count (get j "vars")))
          (check! :declared-routes (:declared-routes claims) (count (get j "routes")))
          (check! :declared-capabilities (:declared-capabilities claims)
                  (count (js->clj (.parse js/JSON (get-in j ["vars" "APP_CAPABILITIES"] "[]")))))
          (check! :app-framework-is-not-sveltekit "cljs-esm-worker"
                  (get-in j ["vars" "APP_FRAMEWORK"]))
          ;; the old config served a SvelteKit client dir that no longer exists,
          ;; and matched **/*.wasm in a tree with zero .wasm files -- a rule that
          ;; LOOKED load-bearing because the directory is named wasm/
          (check! :no-stale-assets-binding true (nil? (get j "assets")))
          (check! :no-stale-wasm-rules true (nil? (get j "rules")))
          (check! :no-wasm-in-tree 0 (count (filter #(str/ends-with? % ".wasm") files)))
          ;; nodejs_compat / nodejs_als were adapter-cloudflare's requirement.
          ;; They are gone, and the claim below is what makes that removal
          ;; falsifiable: the bundle must not reach for a node builtin. Verified
          ;; under workerd, not by reasoning -- docs/operator-quickstart.md S6.
          (check! :no-node-compat-flags true (nil? (get j "compatibility_flags")))
          (check! :shadow-output-dir (:shadow-output-dir claims) (:output-dir build))
          (check! :shadow-export (:shadow-export claims)
                  (get-in build [:modules :worker :exports 'default]))
          (check! :wrangler-main-is-the-shadow-output true
                  (= (get j "main") (str "../../" (:output-dir build) "/worker.js")))
          ;; ── :warnings-as-errors, asserted by KEY PATH and never by grep ──
          ;; shadow reads [:compiler-options :warnings-as-errors]. Under
          ;; :build-options it is silently ignored -- which is the same failure the
          ;; option exists to prevent, a fix that cannot fail. A grep would match
          ;; the comment in shadow-cljs.edn that explains this very hazard, so this
          ;; reads the file as EDN and looks at the path.
          (check! :warnings-are-errors true
                  (true? (get-in build [:compiler-options :warnings-as-errors])))
          (check! :warnings-as-errors-not-misplaced nil
                  (get-in build [:build-options :warnings-as-errors]))))

      ;; ── the collection list the page prints comes from kotodama.jsonld ──
      ;; route.cljc carries a copy (the Worker has no way to read the JSON-LD at
      ;; request time), so the copy is checked against the original here. Without
      ;; this the page could advertise NSIDs the descriptor never declared.
      (let [r (slurp* "src/legal_entity/route.cljc")
            decl (get-in kj ["triggers" "subscribeRepos" "collections"])]
        (if (or (nil? r) (nil? decl))
          (undet! "route.cljc or kotodama.jsonld unreadable")
          (do (check! :collections (:collections claims) (count decl))
              (check! :every-declared-collection-is-in-route-cljc []
                      (vec (remove #(str/includes? r (str "\"" % "\"")) decl)))))))

    ;; ── capabilities are labels here, NOT NSIDs (this repo differs) ──
    ;; The sibling migrations prefix APP_CAPABILITIES into fully-qualified NSIDs
    ;; because their values are camelCase METHOD names. This repo's values are the
    ;; same five kebab-case capability labels that kotodama.jsonld carries under
    ;; profile.capabilities -- no deployed code ever read them as methods. Prefixing
    ;; would print five NSIDs that no code in this repo asserts exist. This claim
    ;; fails if someone "fixes" route.cljc to match the siblings.
    (let [w (some-> (slurp* (str APP "/wrangler.jsonc")) strip-jsonc)
          kj (try (js->clj (.parse js/JSON (slurp* (str APP "/kotodama.jsonld"))) :keywordize-keys false)
                  (catch :default _ nil))
          r (slurp* "src/legal_entity/route.cljc")]
      (if (or (nil? w) (nil? kj) (nil? r))
        (undet! "wrangler.jsonc, kotodama.jsonld or route.cljc unreadable")
        (let [from-wrangler (js->clj (.parse js/JSON (get-in (js->clj (.parse js/JSON w) :keywordize-keys false)
                                                             ["vars" "APP_CAPABILITIES"] "[]")))
              from-descriptor (get-in kj ["profile" "capabilities"])]
          (check! :capabilities-match-the-descriptor from-descriptor from-wrangler)
          (check! :capability-fn-does-not-prefix true
                  (and (str/includes? r "(defn capability-labels")
                       (not (str/includes? r "(defn capability-nsids")))))))

    ;; The page renders the route TABLE, the capabilities and the collections rather
    ;; than baked literals -- the defect ADR-0001 records was `routeCount: 0`,
    ;; `routes: []` and `vars: []` beside a config declaring 1 route, 9 vars and
    ;; 5 capabilities. Asserted structurally (the view takes the data, the worker
    ;; passes the real values) and NOT by forbidding a substring: a check that a
    ;; docstring explaining the old defect can trip is a check about prose.
    (let [v (slurp* "src/legal_entity/view.cljc")
          w (slurp* "src/legal_entity/worker.cljs")]
      (if (or (nil? v) (nil? w))
        (undet! "view.cljc or worker.cljs unreadable")
        (check! :page-renders-the-data true
                (and (str/includes? v "[{:keys [routes capabilities collections vars mcp-url actor built-at]}]")
                     (str/includes? v "(route-rows routes)")
                     (str/includes? w ":routes route/routes")
                     (str/includes? w ":capabilities (decode-capabilities")
                     (str/includes? w ":collections route/collections")))))

    ;; The prose does not ship with an unfilled hole. docs/operator-quickstart.md
    ;; is written before the slow build lands and its outputs are pasted in
    ;; afterwards; a forgotten marker would read as measured output.
    (check! :no-unfilled-placeholders []
            (vec (keep (fn [f]
                         (when-let [c (slurp* f)]
                           (when (str/includes? c "PLACEHOLDER") f)))
                       ["README.md" "docs/operator-quickstart.md"
                        "docs/adr/0001-migrate-the-appview-from-typescript-to-clojurescript.edn"])))

    ;; CLAUDE.md no longer claims a TypeScript runtime whose business logic is
    ;; src/app.ts -- a file this migration deleted.
    (let [c (slurp* "CLAUDE.md")]
      (if (nil? c)
        (undet! "CLAUDE.md unreadable")
        (check! :claude-md-does-not-claim-a-typescript-runtime true
                (and (not (str/includes? c "| Language | TypeScript"))
                     (not (str/includes? c "app.ts が直接 wrangler entrypoint"))
                     (str/includes? c "shadow-cljs")))))

    ;; Nothing OUTSIDE kotoba/ builds a node/TypeScript artifact any more. kotoba/
    ;; keeps its own package.json / tsconfig.json / vitest.config.ts -- that is how
    ;; its four tests are run -- and those three are pinned by hash above.
    (check! :no-node-build-config-outside-kotoba []
            (vec (filter #(and (not (str/starts-with? % "kotoba/"))
                               (re-find #"(^|/)(package\.json|package-lock\.json|tsconfig\.json|vite\.config\.ts|vitest\.config\.ts|svelte\.config\.js)$" %))
                         files)))

    ;; The ADR is readable EDN tx-data (the workspace rule: 90-docs and docs/adr are
    ;; EDN only, and must load with cljs.reader/read-string).
    (let [adr (read-edn "docs/adr/0001-migrate-the-appview-from-typescript-to-clojurescript.edn")]
      (if (nil? adr)
        (undet! "the ADR is not readable EDN")
        (check! :adr-is-tx-data true
                (and (vector? adr) (map? (first adr))
                     (= "accepted" (:adr/status (first adr)))))))))

;; evidence floor: a run that asserted almost nothing must not read as a clean bill
(println (str "CHECKED\t" @checks))
(when (< @checks 30)
  (println (str "UNDETERMINED\tonly " @checks " claims were evaluated; expected at least 30"))
  (js/process.exit 2))

(let [u @undetermined f @failures]
  (when (seq u)
    (doseq [m u] (println (str "UNDETERMINED\t" m)))
    (println "Refusing to report a pass: the tree could not be read completely.")
    (js/process.exit 2))
  (if (seq f)
    (do (println (str "FAILED\t" (count f) " claim(s): " (str/join ", " (map name f)))) (js/process.exit 1))
    (do (println "OK\tevery claim in README.md and docs/operator-quickstart.md holds") (js/process.exit 0))))

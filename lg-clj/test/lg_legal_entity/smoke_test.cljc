(ns lg-legal-entity.smoke-test
  "Smoke tests for the lg-legal-entity clj port — clojure.test analogue of the
  Python `tests/test_smoke.py`, plus node-behaviour tests the original could not
  run offline (collectors are injectable here, so the dispatch + single-node
  graph logic verify under bb with stubs)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.set :as set]
            [langgraph.graph :as g]
            [lg-legal-entity.server :as server]
            [lg-legal-entity.graphs.health :as health]
            [lg-legal-entity.graphs.task :as task]))

(def registry-suffixes
  ["Jpn" "Gbr" "Fra" "Nor" "Dnk" "Fin" "Est" "Cze" "Nzl" "Che" "Nld" "Isr"])

(def expected-graphs
  (into #{"health" "gleifFetchPages" "gleifRegisterDids"
          "edgarCollectUsa" "edgarIngestSecDisclosure"}
        (map #(str "registryCollect" %) registry-suffixes)))

;; ── server registry parity (mirrors test_smoke.py) ──────────────────────────

(deftest graphs-match-expected-set
  (is (= expected-graphs (set (keys server/GRAPHS))))
  (is (= 17 (count server/GRAPHS)) "health + 16 collectors"))

(deftest nsid-map-references-known-graphs
  (doseq [[nsid gname] server/NSID-MAP]
    (is (contains? server/GRAPHS gname) (str nsid " → " gname " not in GRAPHS"))))

(deftest nsid-map-completeness
  ;; every NSID's assistant value is a valid graph key (Python
  ;; test_nsid_to_assistant_completeness)
  (is (set/subset? (set (vals server/NSID-MAP)) (set (keys server/GRAPHS)))))

(deftest all-graphs-invocable
  (doseq [[nm graph] server/GRAPHS]
    (is (some? graph) (str "GRAPHS[" nm "] nil"))))

;; ── dispatch surface (/ok, /runs, /xrpc) ────────────────────────────────────

(deftest health-endpoint
  (let [r (server/health)]
    (is (= 200 (:status r)))
    (is (true? (get-in r [:body :ok])))
    (is (= expected-graphs (set (get-in r [:body :graphs]))))))

(deftest unknown-assistant-404
  (is (= 404 (:status (server/dispatch-run {:assistant_id "nope" :input {}})))))

(deftest unknown-xrpc-404
  (is (= 404 (:status (server/dispatch-xrpc "com.etzhayyim.legalEntity.unknownMethod" {})))))

(deftest runs-default-assistant-is-health
  ;; assistant_id omitted → defaults to "health" (Python body.get default)
  (let [r (server/dispatch-run {})]
    (is (= 200 (:status r)))
    (is (= {:status "ok" :service "lg-legal-entity"} (get-in r [:body :output])))))

;; ── health graph end-to-end ─────────────────────────────────────────────────

(deftest health-graph-invokes
  (is (= {:status "ok" :service "lg-legal-entity"}
         (:result (g/invoke health/GRAPH {:input {}})))))

;; ── single-node collector graph behaviour (injectable handler seam) ─────────

(deftest collector-default-handler-not-configured
  ;; no handler bound → default-handler stub, never raises
  (let [out (g/invoke (server/GRAPHS "gleifFetchPages") {:input {:page 1}})]
    (is (= "not-configured" (get-in out [:result :status])))
    (is (= "gleifFetchPages" (get-in out [:result :task])))
    (is (= {:page 1} (get-in out [:result :input])))))

(deftest collector-injected-handler-runs
  (binding [task/*handlers* {"edgarCollectUsa" (fn [input] {:collected 42 :echo input})}]
    (let [out (g/invoke (server/GRAPHS "edgarCollectUsa") {:input {:cik "0000320193"}})]
      (is (= 42 (get-in out [:result :collected])))
      (is (= {:cik "0000320193"} (get-in out [:result :echo]))))))

(deftest collector-handler-error-captured
  ;; a throwing handler → {:error ...}, graph never raises (Python try/except)
  (binding [task/*handlers* {"registryCollectJpn"
                             (fn [_input] (throw (ex-info "boom upstream" {})))}]
    (let [out (g/invoke (server/GRAPHS "registryCollectJpn") {:input {}})]
      (is (re-find #"boom upstream" (:error out)))
      (is (nil? (:result out))))))

(deftest dispatch-run-through-injected-collector
  (binding [task/*handlers* {"gleifRegisterDids" (fn [_input] {:dids ["did:lei:x"]})}]
    (let [r (server/dispatch-run {:assistant_id "gleifRegisterDids" :input {}})]
      (is (= 200 (:status r)))
      (is (= ["did:lei:x"] (get-in r [:body :output :dids]))))))

(deftest dispatch-run-collector-error-is-500
  (binding [task/*handlers* {"edgarIngestSecDisclosure"
                             (fn [_input] (throw (ex-info "rate limited" {})))}]
    (let [r (server/dispatch-run {:assistant_id "edgarIngestSecDisclosure" :input {}})]
      (is (= 500 (:status r)))
      (is (re-find #"rate limited" (get-in r [:body :error]))))))

(deftest dispatch-xrpc-maps-nsid
  (binding [task/*handlers* {"registryCollectGbr" (fn [_input] {:ok true})}]
    (let [r (server/dispatch-xrpc "com.etzhayyim.legalEntity.registryCollectGbr" {})]
      (is (= 200 (:status r)))
      (is (true? (get-in r [:body :output :ok]))))))

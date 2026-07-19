(ns lg-legal-entity.server
  "lg-legal-entity dispatch surface — clj port of `lg/lg_legal_entity/server.py`
  (ADR-2606280030).

  The Python file is a FastAPI app exposing:
    GET  /ok /health      → liveness / readiness
    POST /runs            → invoke graph by assistant_id
    POST|GET /xrpc/{nsid} → XRPC shim (NSID → graph mapping)

  It registers 17 graphs: a `health` graph + 16 single-node collector graphs
  (GLEIF / EDGAR / 12 per-country registry tasks).  This namespace ports the
  graph REGISTRY (GRAPHS / NSID-MAP) and the invoke/serialize routing as plain
  clj functions (`dispatch-run`, `dispatch-xrpc`, `health`).  Wrapping these in a
  concrete HTTP server (http-kit / ring) is left to the deployment layer — the
  graph registry + dispatch is the load-bearing port.  The Python FastAPI server
  (`lg/`) remains the deployed runtime and COEXISTS (py_removed = 0).

  Deviations from the Python server, all toward the proven wave-1 idiom:
   * Unknown NSID returns 404 (Python distinguishes 501 'unmapped' vs 404
     'missing graph'; the clj twin collapses both to 404 like the recap twin).
   * /ok and /health return {:ok true :graphs [...]} (self-consistent registry
     liveness), matching the wave-1 smoke contract rather than the drifted
     Python /ok ({:ok true} only) / /health (raw health-graph result) split.
   * No RetryPolicy (langgraph-clj has none); the Python graphs declared none
     either, so topology parity is exact."
  (:require [clojure.string :as str]
            [langgraph.graph :as g]
            [lg-legal-entity.graphs.health :as health]
            [lg-legal-entity.graphs.task :as task]))

;; ── task registry (NSID → graph) ────────────────────────────────────────────
;; type legalEntity.X.Y → assistant key = last NSID segment (Python rsplit('.',1)).

(def registry-suffixes
  ["Jpn" "Gbr" "Fra" "Nor" "Dnk" "Fin" "Est" "Cze" "Nzl" "Che" "Nld" "Isr"])

(def task-nsids
  "The 16 collector NSIDs, in the same order as the Python TASKS dict."
  (into ["com.etzhayyim.legalEntity.gleifFetchPages"
         "com.etzhayyim.legalEntity.gleifRegisterDids"
         "com.etzhayyim.legalEntity.edgarCollectUsa"
         "com.etzhayyim.legalEntity.edgarIngestSecDisclosure"]
        (map #(str "com.etzhayyim.legalEntity.registryCollect" %) registry-suffixes)))

(defn- last-segment [nsid] (last (str/split nsid #"\.")))

(def GRAPHS
  "assistant-id → compiled graph. health + 16 collector graphs (17 total)."
  (into {"health" health/GRAPH}
        (map (fn [nsid]
               (let [k (last-segment nsid)]
                 [k (task/make-graph k)]))
             task-nsids)))

(def NSID-MAP
  "NSID → assistant-id (graph key). Mirrors Python `_NSID_TO_ASSISTANT`."
  (into {"com.etzhayyim.legalEntity.health" "health"}
        (map (fn [nsid] [nsid (last-segment nsid)]) task-nsids)))

;; ── invoke / serialize ──────────────────────────────────────────────────────

(defn- clip [s n] (let [s (str s)] (subs s 0 (min n (count s)))))

(defn- run-graph
  "Invoke graph with {:input input}; serialize like the Python /runs response.
  Success → {:status 200 :body {:output result}}; node error → {:status 500
  :body {:error ...}}; never throws."
  [graph input]
  (try
    (let [state (g/invoke graph {:input (or input {})})]
      (if-let [err (:error state)]
        {:status 500 :body {:error (clip err 300)}}
        {:status 200 :body {:output (:result state)}}))
    (catch #?(:clj Exception :cljs :default) e
      {:status 500 :body {:error (clip #?(:clj (.getMessage e) :cljs (ex-message e)) 300)}})))

(defn health
  "GET /ok | /health → {:ok true :graphs [...]} (registry liveness)."
  []
  {:status 200 :body {:ok true :graphs (vec (keys GRAPHS))}})

(defn dispatch-run
  "POST /runs body → {:status :body}. body keys: :assistant_id (default
  \"health\"), :input. Mirrors the Python `/runs` handler."
  [body]
  (let [aid   (or (:assistant_id body) "health")
        graph (get GRAPHS aid)]
    (if (nil? graph)
      {:status 404 :body {:error (str "graph '" aid "' not found")}}
      (run-graph graph (or (:input body) {})))))

(defn dispatch-xrpc
  "POST|GET /xrpc/{nsid} → {:status :body}. NSID mapped to a graph via NSID-MAP;
  body is the graph input. Unmapped NSID → 404 (see ns deviations)."
  [nsid body]
  (let [gname (get NSID-MAP nsid)]
    (if (nil? gname)
      {:status 404 :body {:error (str "NSID not mapped: " nsid)}}
      (let [graph (get GRAPHS gname)]
        (if (nil? graph)
          {:status 404 :body {:error (str "graph '" gname "' not found")}}
          (run-graph graph (or body {})))))))

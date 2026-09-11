(ns lg-legal-entity.graphs.health
  "legal-entity `health` graph — liveness check.

  Faithful clj port of `_make_health_graph()` in `lg/lg_legal_entity/server.py`
  (ADR-2606280030). Topology: START → ping → END. The `ping` node returns
  {:result {:status \"ok\" :service \"lg-legal-entity\"}}, mirroring the Python
  health node (which wraps its payload under the `result` state key)."
  (:require [langgraph.graph :as g]))

(defn node-ping [_state]
  {:result {:status "ok" :service "lg-legal-entity"}})

(defn build
  "Compile the health StateGraph (START → ping → END)."
  []
  (-> (g/state-graph)
      (g/add-node :ping node-ping)
      (g/set-entry-point :ping)
      (g/set-finish-point :ping)
      (g/compile-graph)))

(def GRAPH (build))

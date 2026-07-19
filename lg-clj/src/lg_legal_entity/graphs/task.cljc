(ns lg-legal-entity.graphs.task
  "Single-node collector StateGraph factory — clj port of
  `_make_single_node_graph()` in `lg/lg_legal_entity/server.py` (ADR-2606280030).

  The Python server wraps 16 legal-entity collector handlers (GLEIF / EDGAR /
  per-country registry tasks from `kotodama.primitives.legal_entity`) each in an
  identical one-node graph:  START → execute → END.  The `execute` node calls the
  handler with the input map; success yields {:result ...}, any thrown error
  yields {:error <first-300-chars>} (the graph never raises — parity with the
  Python `try/except` node).

  SUBSTRATE BOUNDARY (recipe step 3): the real handlers reach external registry
  APIs (httpx) and persist into RisingWave via psycopg — both forbidden in the
  clj substrate (RisingWave is off-limits; the kotoba-Datom-log is the sanctioned
  target).  So the handler is an INJECTABLE seam: `*handlers*` maps a task name to
  a `(input -> result)` fn.  The default (`default-handler`) returns a structured
  'boundary not wired' stub instead of touching RisingWave, so every graph loads
  and invokes offline under bb.  Tests / a real deployment rebind `*handlers*`
  (the actor swap pattern) to supply live collectors or stubs.  See
  SUBSTRATE-PORT-PENDING.md for the dispatcher-side RisingWave rewrite that stays
  deferred."
  (:require [langgraph.graph :as g]))

(defn- clip
  "First n chars of s (error-message truncation parity with Python's [:300])."
  [s n]
  (let [s (str s)] (subs s 0 (min n (count s)))))

(defn default-handler
  "Fallback collector: no live registry API / RisingWave write in the clj twin.
  Returns a structured stub describing the unwired boundary + echoing the input."
  [task-name input]
  {:status "not-configured"
   :task   task-name
   :note   "collector boundary not wired in clj twin (substrate forbids RisingWave; see SUBSTRATE-PORT-PENDING.md)"
   :input  input})

(def ^:dynamic *handlers*
  "name -> (input-map -> result-map). Empty by default → default-handler is used.
  Rebind (binding / with-redefs) to inject live collectors or test stubs."
  {})

(defn make-node
  "Build the `execute` node fn for a given task name. Reads (:input state),
  dispatches to *handlers* (or default-handler), wraps success/error like the
  Python node."
  [task-name]
  (fn [state]
    (let [input   (or (:input state) {})
          handler (get *handlers* task-name #(default-handler task-name %))]
      (try
        {:result (handler input)}
        (catch #?(:clj Exception :cljs :default) e
          {:error (clip #?(:clj (.getMessage e) :cljs (ex-message e)) 300)})))))

(defn make-graph
  "Compile a single-node collector StateGraph (START → execute → END) for
  task-name. Mirrors `_make_single_node_graph(handler, name)`."
  [task-name]
  (-> (g/state-graph)
      (g/add-node :execute (make-node task-name))
      (g/set-entry-point :execute)
      (g/set-finish-point :execute)
      (g/compile-graph)))

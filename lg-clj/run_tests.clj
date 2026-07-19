;; lg-legal-entity clj-port test runner (repo rule: run_tests.clj, NOT .sh).
;;
;;   bb run_tests.clj      (from 60-apps/etzhayyim-project-legal-entity/lg-clj/)
;;   bb test               (bb.edn task alias)
;;
;; Exits non-zero if any test fails or errors.
(require '[clojure.test :as t]
         'lg-legal-entity.smoke-test)

(let [{:keys [fail error]} (t/run-tests 'lg-legal-entity.smoke-test)]
  (when (pos? (+ (or fail 0) (or error 0)))
    (System/exit 1)))

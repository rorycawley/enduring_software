(ns foo.perf.cli
  (:require [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [foo.perf.evaluate :as evaluation]))

(defn- parse-args [args]
  (let [enforce? (= "--enforce" (first args))
        values (if enforce? (rest args) args)]
    (when (< (count values) 2)
      (throw (ex-info "Usage: [--enforce] perf/budgets.edn run-1.json ..." {})))
    {:enforce? enforce?
     :budget-path (first values)
     :reports (rest values)}))

(defn -main [& args]
  (try
    (let [{:keys [enforce? budget-path reports]} (parse-args args)
          budget (edn/read-string (slurp budget-path))
          evidence (mapv #(json/read-str (slurp %)) reports)
          result (evaluation/evaluate budget evidence)
          {summary :summary status :status} result]
      (println (str "HTTP performance evaluation: " (str/upper-case (name status))))
      (println (format "Median p95: %.3f ms; worst p95: %.3f ms; worst p99: %.3f ms; requests: %d"
                       (:median-p95-ms summary) (:worst-p95-ms summary)
                       (:worst-p99-ms summary) (:total-requests summary)))
      (doseq [failure (:failures result)] (println "FAIL:" failure))
      (doseq [warning (:warnings result)] (println "WARNING:" warning))
      (when-not enforce?
        (println "Report-only: use make perf-check for enforceable provisional HTTP budgets."))
      (System/exit (if (and enforce? (= status :fail)) 1 0)))
    (catch Exception e
      (binding [*out* *err*]
        (println "Invalid performance evidence:" (.getMessage e))
        (when (ex-data e) (prn (ex-data e))))
      (System/exit 2))))

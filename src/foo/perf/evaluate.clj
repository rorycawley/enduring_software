(ns foo.perf.evaluate
  "Pure evaluation of repeated Vegeta JSON reports against a documented API budget.
   This does NOT establish browser-visible user responsiveness.")

(defn- finite-number? [x]
  (and (number? x)
       (not (Double/isNaN (double x)))
       (not (Double/isInfinite (double x)))))

(defn- require-valid! [valid? message data]
  (when-not valid?
    (throw (ex-info message data))))

(defn- metric [report key]
  (get-in report ["latencies" key]))

(defn- valid-report! [report index minimum-requests]
  (let [requests (get report "requests")
        success (get report "success")
        p50 (metric report "50th")
        p95 (metric report "95th")
        p99 (metric report "99th")
        codes (get report "status_codes")
        errors (get report "errors")]
    (require-valid! (and (integer? requests) (>= requests minimum-requests))
                    "Invalid or insufficient request count"
                    {:run index :requests requests :minimum minimum-requests})
    (require-valid! (and (finite-number? success) (<= 0 success 1))
                    "Missing or invalid success ratio" {:run index :success success})
    (require-valid! (and (every? finite-number? [p50 p95 p99])
                         (<= 0 p50 p95 p99))
                    "Missing or invalid latency percentiles"
                    {:run index :latencies (get report "latencies")})
    (require-valid! (and (map? codes)
                         (every? (fn [[_ count]] (and (integer? count) (<= 0 count))) codes)
                         (= requests (reduce + 0 (vals codes))))
                    "Invalid HTTP status-code counts" {:run index :status-codes codes})
    (require-valid! (and (vector? errors) (every? string? errors))
                    "Missing or invalid error list" {:run index :errors errors})
    {:run index
     :requests requests
     :success success
     :http-200 (get codes "200" 0)
     :errors errors
     :p50-ms (/ (double p50) 1000000.0)
     :p95-ms (/ (double p95) 1000000.0)
     :p99-ms (/ (double p99) 1000000.0)}))

(defn- median [numbers]
  (let [sorted (vec (sort numbers))
        n (count sorted)
        midpoint (quot n 2)]
    (if (odd? n)
      (nth sorted midpoint)
      (/ (+ (nth sorted (dec midpoint)) (nth sorted midpoint)) 2.0))))

(defn evaluate
  "Return {:status :pass/:warning/:fail, :runs ..., :summary ..., :failures ..., :warnings ...}.
   Throws ex-info for invalid reports/budgets. All runs must satisfy absolute limits;
   the median p95 is used only for environment-matched, optional regression warnings."
  [budget reports]
  (let [{:keys [run-count minimum-requests-per-run max-p95-ms max-p99-ms
                baseline-p95-ms warn-regression-factor warn-regression-min-ms]} budget]
    (require-valid! (and (integer? run-count) (pos? run-count)
                         (integer? minimum-requests-per-run) (pos? minimum-requests-per-run)
                         (every? #(and (finite-number? %) (pos? %))
                                 [max-p95-ms max-p99-ms warn-regression-factor])
                         (finite-number? warn-regression-min-ms)
                         (<= 0 warn-regression-min-ms)
                         (or (nil? baseline-p95-ms)
                             (and (finite-number? baseline-p95-ms) (pos? baseline-p95-ms))))
                    "Invalid performance budget" {:budget budget})
    (require-valid! (and (sequential? reports) (= run-count (count reports)))
                    "Unexpected number of performance reports"
                    {:expected run-count :actual (count reports)})
    (let [runs (mapv (fn [i report]
                       (valid-report! report (inc i) minimum-requests-per-run))
                     (range) reports)
          median-p95 (median (map :p95-ms runs))
          max-p95 (apply max (map :p95-ms runs))
          min-p95 (apply min (map :p95-ms runs))
          max-p99 (apply max (map :p99-ms runs))
          failures (vec (mapcat (fn [{:keys [run requests http-200 errors success p95-ms p99-ms]}]
                                  (concat
                                   (when (or (not= requests http-200)
                                             (< success 1.0)
                                             (seq errors))
                                     [(str "Run " run ": not all requests completed with HTTP 200 and no errors")])
                                   (when (> p95-ms max-p95-ms)
                                     [(format "Run %d: p95 %.3f ms exceeds %.3f ms budget"
                                              run p95-ms (double max-p95-ms))])
                                   (when (> p99-ms max-p99-ms)
                                     [(format "Run %d: p99 %.3f ms exceeds %.3f ms budget"
                                              run p99-ms (double max-p99-ms))])))
                                runs))
          warnings (vec
                    (concat
                     (when (and baseline-p95-ms
                                (>= median-p95 (* baseline-p95-ms warn-regression-factor))
                                (>= (- median-p95 baseline-p95-ms) warn-regression-min-ms))
                       [(format "Comparable baseline p95 %.3f ms → %.3f ms; investigate regression"
                                (double baseline-p95-ms) median-p95)])
                     (when (and (> min-p95 0)
                                (>= (/ max-p95 min-p95) 2.0)
                                (>= (- max-p95 min-p95) 5.0))
                       [(format "Run-to-run p95 variation is high: %.3f–%.3f ms"
                                min-p95 max-p95)])))]
      {:status (cond (seq failures) :fail
                     (seq warnings) :warning
                     :else :pass)
       :runs runs
       :summary {:median-p95-ms median-p95
                 :worst-p95-ms max-p95
                 :worst-p99-ms max-p99
                 :total-requests (reduce + (map :requests runs))}
       :failures failures
       :warnings warnings})))

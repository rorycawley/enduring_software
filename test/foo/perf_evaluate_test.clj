(ns foo.perf-evaluate-test
  (:require [clojure.test :refer [deftest is testing]]
            [foo.perf.evaluate :as subject]))

(def budget {:run-count 3
             :minimum-requests-per-run 100
             :max-p95-ms 100.0
             :max-p99-ms 250.0
             :baseline-p95-ms nil
             :warn-regression-factor 1.5
             :warn-regression-min-ms 5.0})

(defn report
  ([p95-ms] (report p95-ms 1.0 200 []))
  ([p95-ms success http-status errors]
   {"requests" 200
    "success" success
    "latencies" {"50th" 1000000
                 "95th" (long (* p95-ms 1000000))
                 "99th" (long (* p95-ms 1300000))}
    "status_codes" {(str http-status) 200}
    "errors" errors}))

(deftest passing-performance-acceptance
  (let [result (subject/evaluate budget [(report 2) (report 3) (report 4)])]
    (is (= :pass (:status result)))
    (is (= 3.0 (get-in result [:summary :median-p95-ms])))
    (is (= 4.0 (get-in result [:summary :worst-p95-ms])))
    (is (= 600 (get-in result [:summary :total-requests])))))

(deftest exceeding-budget-is-failure-even-if-median-is-fast
  (let [result (subject/evaluate budget [(report 2) (report 3) (report 110)])]
    (is (= :fail (:status result)))
    (is (some #(re-find #"p95" %) (:failures result)))))

(deftest request-errors-and-unexpected-http-statuses-are-failures
  (testing "Vegeta success counts redirects as success, but greeting requires HTTP 200"
    (is (= :fail (:status (subject/evaluate budget
                                          [(report 2) (report 2 1.0 302 []) (report 2)])))))
  (testing "Errors must not be ignored"
    (is (= :fail (:status (subject/evaluate budget
                                          [(report 2) (report 2 0.9 200 ["timeout"]) (report 2)]))))))

(deftest regression-warning-needs-comparable-baseline-and-meaningful-change
  (let [with-baseline (assoc budget :baseline-p95-ms 3.0)]
    (is (= :warning (:status (subject/evaluate with-baseline
                                            [(report 9) (report 10) (report 11)]))))
    (is (= :pass (:status (subject/evaluate with-baseline
                                         [(report 3.1) (report 3.2) (report 3.3)]))))))

(deftest invalid-evidence-is-not-a-pass
  (testing "missing latency"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"latency" (subject/evaluate budget
                                                         [(dissoc (report 2) "latencies") (report 2) (report 2)]))))
  (testing "unexpected report count"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"number" (subject/evaluate budget [(report 2)]))))
  (testing "too few requests"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"request count" (subject/evaluate budget
                                                               [(assoc (report 2) "requests" 4)
                                                                (report 2) (report 2)])))))

(deftest unstable-measurement-warns
  (is (= :warning (:status (subject/evaluate budget [(report 1) (report 2) (report 8)])))))

(ns user
  (:require [clojure.tools.namespace.repl :as tn]
            [clojure.test :as t]))

(tn/set-refresh-dirs "src" "test")

(defn run-tests []
  (t/run-tests 'foo.greeting-test))

(defn check []
  (tn/refresh :after 'user/run-tests))

(defn go []
  (tn/refresh :after 'foo.core/-main))
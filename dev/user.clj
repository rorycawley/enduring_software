(ns user
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :as tn]
            [clojure.test :as t]))

(tn/set-refresh-dirs "src" "test")

(defonce system (atom nil))

(defn start []
  (when-not @system
    (let [new-system (requiring-resolve 'foo.system/new-system)]
      (reset! system
              (component/start
               (new-system {:port 3000})))))
  :started)

(defn stop []
  (when-let [running @system]
    (component/stop running)
    (reset! system nil))
  :stopped)

(defn go []
  (stop)
  (tn/refresh :after 'user/start))

(defn run-tests []
  (t/run-tests 'foo.greeting-test
               'foo.http-test
               'foo.system-test))

(defn check []
  (tn/refresh :after 'user/run-tests))
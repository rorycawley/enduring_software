(ns foo.system-test
  (:require [clojure.test :refer [deftest is]]
            [com.stuartsierra.component :as component]
            [foo.system :as system]))

(deftest jetty-lifecycle-test
  (let [running (component/start
                 (system/new-system {:port 0}))
        jetty (get-in running [:jetty :server])]

    (try
      (let [connector (first (.getConnectors jetty))
            port (.getLocalPort connector)]

        (is (.isStarted jetty))

        (is (= "Welcome to Clojure, World!"
               (slurp
                (str "http://127.0.0.1:"
                     port
                     "/hello"))))
        (is (= "OK"
               (slurp
                (str "http://127.0.0.1:"
                     port
                     "/health")))))
      (finally
        (component/stop running)))

    (is (.isStopped jetty))))
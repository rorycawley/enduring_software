(ns foo.acceptance.health-test
  (:require [clojure.test :refer [deftest is]]
            [com.stuartsierra.component :as component]
            [foo.system :as system]))

(deftest application-is-responsive
  (let [running (component/start
                 (system/new-system {:port 0}))]
    (try
      (let [server (get-in running [:jetty :server])
            port (.getLocalPort
                  (first (.getConnectors server)))
            url (str "http://127.0.0.1:"
                     port
                     "/health")]

        (is (= "OK" (slurp url))))

      (finally
        (component/stop running)))))
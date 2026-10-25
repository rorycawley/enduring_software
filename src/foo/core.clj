(ns foo.core
  (:require [com.stuartsierra.component :as component]
            [foo.system :as system]))

(defn -main [& _args]
  (let [running (component/start
                 (system/new-system {:port 3000}))
        jetty (get-in running [:jetty :server])]

    (try
      (.join jetty)
      (finally
        (component/stop running)))))
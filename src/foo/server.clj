(ns foo.server
  (:require [com.stuartsierra.component :as component]
            [ring.adapter.jetty :as jetty]
            [foo.http :as http]))

(defrecord JettyServer [host port server]

  component/Lifecycle

  (start [this]
    (if server
      this
      (assoc this :server
             (jetty/run-jetty
              http/app
              {:host host
               :port port
               :join? false}))))

  (stop [this]
    (when server
      (.stop server))
    (assoc this :server nil)))

(defn new-server [{:keys [host port]}]
  (map->JettyServer
   {:host host
    :port port}))
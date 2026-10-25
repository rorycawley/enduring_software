(ns foo.system
  (:require [com.stuartsierra.component :as component]
            [foo.server :as server]))

(defn new-system
  [{:keys [host port]
    :or {host "127.0.0.1"
         port 3000}}]

  (component/system-map
   :jetty (server/new-server
           {:host host
            :port port})))
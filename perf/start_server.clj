;; Performance-only launcher. Keeps the application unchanged and uses an
;; OS-selected loopback port so the regular development server is unaffected.
(require '[com.stuartsierra.component :as component]
         '[foo.system :as system])

(let [port-file (System/getenv "PERF_PORT_FILE")]
  (when-not port-file
    (throw (ex-info "PERF_PORT_FILE is required" {})))
  (let [running (component/start (system/new-system {:port 0}))
        server  (get-in running [:jetty :server])
        port    (.getLocalPort (first (.getConnectors server)))]
    (try
      (spit port-file (str port))
      (.join server)
      (finally
        (component/stop running)))))

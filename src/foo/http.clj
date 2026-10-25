(ns foo.http
  (:require [reitit.ring :as ring]
            [ring.middleware.params :refer [wrap-params]]
            [foo.greeting :as greeting]))

(defn hello [request]
  (let [name (get-in request [:query-params "name"])]
    {:status 200
     :headers {"content-type" "text/plain; charset=utf-8"}
     :body (greeting/greet (or (not-empty name) "World"))}))

(defn health [_request]
  {:status 200
   :headers {"content-type" "text/plain; charset=utf-8"}
   :body "OK"})

(def app
  (wrap-params
   (ring/ring-handler
    (ring/router
     [["/hello"  {:get hello}]
      ["/health" {:get health}]])
    (ring/create-default-handler))))
(ns foo.http
  (:require [reitit.ring :as ring]
            [foo.greeting :as greeting]))

(defn hello [_request]
  {:status 200
   :headers {"content-type" "text/plain; charset=utf-8"}
   :body (greeting/greet "World")})

(defn health [_request]
  {:status 200
   :headers {"content-type" "text/plain; charset=utf-8"}
   :body "OK"})

(def app
  (ring/ring-handler
   (ring/router
    [["/hello"  {:get hello}]
     ["/health" {:get health}]])
   (ring/create-default-handler)))
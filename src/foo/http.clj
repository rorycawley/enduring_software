(ns foo.http
  (:require [reitit.ring :as ring]
            [foo.greeting :as greeting]))

(defn hello [_request]
  {:status 200
   :headers {"content-type" "text/plain; charset=utf-8"}
   :body (greeting/greet "World")})

(def app
  (ring/ring-handler
   (ring/router
    [["/hello" {:get hello}]])
   (ring/create-default-handler)))
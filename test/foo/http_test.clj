(ns foo.http-test
  (:require [clojure.test :refer [deftest is]]
            [ring.mock.request :as mock]
            [foo.http :as http]))

(deftest hello-endpoint-test
  (let [request (mock/request :get "/hello")
        response (http/app request)]

    (is (= 200 (:status response)))

    (is (= "text/plain; charset=utf-8"
           (get-in response [:headers "content-type"])))

    (is (= "Welcome to Clojure, World!"
           (:body response)))))

(deftest unknown-route-test
  (let [request (mock/request :get "/unknown")
        response (http/app request)]

    (is (= 404 (:status response)))))
(ns foo.acceptance.greeting-test
  (:require [clojure.test :refer [deftest is]]
            [com.stuartsierra.component :as component]
            [foo.system :as system]))

(defn- http-get [path]
  (let [running (component/start
                 (system/new-system {:port 0}))]
    (try
      (let [server (get-in running [:jetty :server])
            port (.getLocalPort
                  (first (.getConnectors server)))
            url (str "http://127.0.0.1:" port path)
            connection (.openConnection
                        (.toURL (java.net.URI/create url)))]
        {:status (.getResponseCode connection)
         :content-type (.getContentType connection)
         :body (slurp (.getInputStream connection))})
      (finally
        (component/stop running)))))



(deftest greet-person-by-name
  (let [{:keys [status content-type body]}
        (http-get "/hello?name=Ada")]

    (is (= 200 status))

    (is (re-matches
         #"(?i)text/plain\s*;\s*charset=utf-8"
         content-type))

    (is (= "Welcome to Clojure, Ada!" body))))


(deftest greet-person-without-name
  (let [{:keys [status body]}
        (http-get "/hello")]

    (is (= 200 status))

    (is (= "Welcome to Clojure, World!"
           body))))



(deftest greet-person-with-empty-name
  (let [{:keys [status body]}
        (http-get "/hello?name=")]

    (is (= 200 status))

    (is (= "Welcome to Clojure, World!"
           body))))
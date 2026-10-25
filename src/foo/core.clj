(ns foo.core
  (:require [foo.greeting :as greeting]))

(defn -main [& _args]
  (println (greeting/greet "World")))
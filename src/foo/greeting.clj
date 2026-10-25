(ns foo.greeting
  (:require [clojure.string :as str]))

(defn greet [name]
  (str "Welcome to Clojure, "
       (if (str/blank? name) "friend" name)
       "!"))
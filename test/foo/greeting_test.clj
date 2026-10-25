(ns foo.greeting-test
  (:require [clojure.test :refer [deftest is]]
            [foo.greeting :as greeting]))

(deftest greet-test
  (is (= "Welcome to Clojure, World!"
         (greeting/greet "World"))))

(deftest greet-empty-name-test
  (is (= "Welcome to Clojure, friend!"
         (greeting/greet ""))))

(deftest greet-whitespace-name-test
  (is (= "Welcome to Clojure, friend!"
         (greeting/greet "   "))))
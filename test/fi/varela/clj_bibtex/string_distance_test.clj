(ns fi.varela.clj-bibtex.string-distance-test
  (:require [fi.varela.clj-bibtex.string-distance :as sut]
            [clojure.test :refer :all]))

(deftest non-empty
  (testing "If both strings are empty, it throws"
    (is (thrown? java.lang.AssertionError (sut/sorensen-dice "" "")))))

(deftest equal-results
  (testing "All bigrams are the same, we get 1.0"
    (is (= 1.0 (sut/sorensen-dice "a" "a")))
    (is (= 1.0 (sut/sorensen-dice "aa" "aaaa")))))

(deftest no-similarities
  (testing "All bigrams are different, we get 0.0"
    (is (= 0.0 (sut/sorensen-dice "a" "b")))
    (is (= 0.0 (sut/sorensen-dice "Toto Tato" "Pipi Popi")))))

(deftest known-values
  (testing "Expected values")
  (is (= 0.8 (sut/sorensen-dice "foo" "foo "))))

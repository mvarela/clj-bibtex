(ns fi.varela.clj-bibtex.string-distance
  (:require [clojure.set :as set]))

(defn bigrams
  "Creates a set of all bigrams in `s`"
  [s]
  (->> s
       (partition 2 1)
       (map #(apply str %))
       (into #{})))

(defn sorensen-dice
  "Computes the [Sørenson-Dice coefficient](https://en.wikipedia.org/wiki/S%C3%B8rensen%E2%80%93Dice_coefficient) between `s1` and `s2`.
  At least one of the arguments must have length > 0."
  [s1 s2]
  {:pre [(or (> (count s1) 0)
             (> (count s2) 0))]}
  (if (or (= 1 (count s1))
          (= 1 (count s2)))
    (if (= s1 s2) 1.0 0.0)
    (let [b1 (bigrams s1)
          b2 (bigrams s2)]
      (/ (* 2.0 (count (set/intersection b1 b2)))
         (+ (count b1) (count b2))))))



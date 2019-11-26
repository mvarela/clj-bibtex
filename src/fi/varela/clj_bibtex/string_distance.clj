(ns fi.varela.clj-bibtex.string-distance
  (:require [clojure.set :as set]))

(defn bigrams [s]
  (->> s
       (partition 2 1)
       (map #(apply str %))
       (into #{})))

(defn jaccard [s1 s2]
  (let [b1 (bigrams s1)
        b2 (bigrams s2)]
    ((comp double /) (count (set/intersection b1 b2))
       (count (set/union b1 b2)))))

(defn sorensen-dice [s1 s2]
  (let [b1 (bigrams s1)
        b2 (bigrams s2)]
    (/ (* 2.0 (count (set/intersection b1 b2)))
       (+ (count b1) (count b2)))))

(comment 
  (sorensen-dice "Speed Index: Relating the Industrial Standard for User Perceived Web Performance to web QoE"
                 "speed index: relating the  standard for  perceived web performance to web qos")
;; => 0.7301587301587301

  (jaccard "Speed Index: Relating the Industrial Standard for User Perceived Web Performance to web QoE"
           "speed index: relating the industrial standard for user perceived web performance to web qoe")
;; => 0.675

(sorensen-dice "Speed Index: Relating the Industrial Standard for User Perceived Web Performance to web QoE"
               "Speed Index: Relating the Industrial Standard for User Percieved Web Performance to web QoE")
;; => 0.9645390070921985
(jaccard "Speed Index: Relating the Industrial Standard for User Perceived Web Performance to web QoE"
         "Speed Index: Relating the Industrial Standard for User Percieved Web Performance to web QoE")
;; => 0.9315068493150686
  )



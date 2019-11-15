(ns fi.varela.clj-bibtex
  "Clojure wrapper for parsing BibTex data using jbibtex"
  (:import [org.jbibtex BibTeXParser BibTeXDatabase BibTeXEntry BibTeXObject BibTeXFormatter Key StringValue LaTeXParser LaTeXPrinter])
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.reflect :as reflect]
            [clojure.string :as string]
            [fipp.clojure :as fipp]))

;;; This namespace provides an idiomatic wrapper for the parsing functionality
;;; of [jbibtex](https://github.com/jbibtex/jbibtex), which while providing great
;;; functionality, is very Java-ish (heavy OO style). The goal is to avoid heavy
;;; interop and easily go from a BibTeX file to a plain Clojure map of entries.


;; We define a few convenience objects we'll use later.
(def bibtex-parser (BibTeXParser.))
(def latex-parser (LaTeXParser.))
(def latex-printer (LaTeXPrinter.))



(def get-field* (memfn getField k))

(defn get-field
  "Extracts `field` (which must be a string) from a BibTexEntry  `entry`"
  [entry field]
  (get-field* entry (Key. field)))

(defn latex->str
  "Converts a LaTeX string `s` into a more readable form (removing curly braces, etc.)"
  [s]
  (.print latex-printer
          (.parse latex-parser
                  (.toUserString s))))

(defn- make-xform
  "Helper function for extracting fields and entry types from the `BibTeXEntry` class. The argument `prefix`
  must be one of `:key` or `:type`"
  [prefix]
  (when (#{:key :type} prefix)
    (comp
     (filter (fn[e]
               (and (= 'org.jbibtex.Key (:type e))
                    (= #{:public :static :final} (:flags e))
                    (re-matches (re-pattern
                                 (str "^" (string/upper-case (name prefix)) ".*"))
                                (str (:name e))))))
     (map (fn[e] ((comp keyword
                    #(string/replace % (re-pattern
                                        (str "^" (name prefix) "_")) "")
                    string/lower-case) (:name e))))
     (map (fn[e]
            [e (string/upper-case (name e))])))))

(defn extract-keys [prefix]
  (when (#{:key :type} prefix)
    (->> (reflect/reflect BibTeXEntry)
         :members
         (into {} (make-xform prefix)))))

(def entry-fields
  (extract-keys :key))

(def entry-types
  (extract-keys :type))

(def entry-types-reverse
  (reduce-kv (fn[acc k v]
               (assoc acc v k)) {} entry-types))

(defn split-authors [author-str]
  (-> author-str
     (string/split #" and ")
     (->> (mapv string/trim))))

(defn ->entry [^BibTeXEntry e]
  (let [base (reduce-kv (fn [acc k v]
                          (let [f (get-field e v)]
                            (if (some? f)
                              (assoc acc k (let [str-val (get-field e v)]
                                             (try (latex->str str-val)
                                                  (catch Exception e (.toUserString str-val)))))
                              acc))) {} entry-fields)
        type  (entry-types-reverse (string/upper-case (.toString (.getType e))))
        year (let [y (:year base)]
                (when (some? y)
                  (try (Integer/parseInt y)
                       (catch Exception e y))))
        author-list (when (some? (:author base))
                      (split-authors (:author base)))]
    (cond-> base
       true (assoc :type type)
       (some? year) (assoc  :year year)
       (some? author-list) (assoc :author author-list))))


(defn parse-bibliography [file-name]
  (->> file-name
     (java.io.FileReader.)
     ((memfn parse f)  bibtex-parser)
     ((memfn getEntries))
     (map (fn [kv]
            [(.toString(.getKey kv)) (->entry (.getValue kv))]))
     (into {})))



(comment 
  (def fname  "/home/mvr/Devel/Clojure/fi.varela.bibtex/resources/samples/literature.bib")
  (def biblio  (parse-bibliography fname))

  biblio


  )

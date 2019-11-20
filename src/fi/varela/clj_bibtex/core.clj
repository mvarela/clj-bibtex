(ns fi.varela.clj-bibtex.core
  "Clojure wrapper for parsing BibTex data using jbibtex"
  (:import [org.jbibtex BibTeXParser BibTeXDatabase BibTeXEntry BibTeXObject BibTeXFormatter Key StringValue LaTeXParser LaTeXPrinter])
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.reflect :as reflect]
            [clojure.string :as string]))

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

(defn- extract-keys [prefix]
  (when (#{:key :type} prefix)
    (->> (reflect/reflect BibTeXEntry)
         :members
         (into {} (make-xform prefix)))))

;; Some utility maps
(def entry-fields
  (extract-keys :key))

(def entry-types
  (extract-keys :type))

(def entry-types-reverse
  (reduce-kv (fn[acc k v]
               (assoc acc v k)) {} entry-types))


(defmulti normalize-author
  "Attempts to normalize an author's name represented by `name-str`. Bib entries
  tend to bi a mish-mash of styles when it comes to writing authors names,
  ending up with things like \"First Last\", \"F. Last\" \"Last, First\", or
  \"Last, F.\". Things like compound names make this even more
  complicated (e.g., if correctly formatted in natural order, it could be
  \"First {Compound Second}\", though the better way to format it would be
  \"Compound Second, First\"). Since we are processing the LaTeX strings before getting
  to this stage, at the moment we won't handle the \"First {Compound Second}\" case."
  (fn
    [name-str]
     (cond
        (re-find #"[,]" name-str) :author-name/last-first
      :else :author-name/first-last)))

(defmethod normalize-author :author-name/last-first
  [name-str]
  name-str)

(defmethod normalize-author :author-name/first-last
  [name-str]
  (let [parts (string/split name-str #"\p{Blank}")
        first (pop parts)
        last (peek parts)]
    (if (< 1 (count parts))
      (string/join ", " [last (string/join " " first)])
      (or last "AUTHOR MISSING"))))

(defn- split-authors
  "Splits the `author-str` string representing the author field in a BibTeX entry
  into normalized author names"
  [author-str]
  (-> author-str
     (string/split #"\p{Space}and\p{Space}")
     (->> (mapv (comp string/trim normalize-author)))))

(defn- process-names
  [base a-key]
  (when (some? (a-key base))
    (let [ns-key (keyword (name a-key) "name")
          a-list (split-authors (a-key base))]
      (mapv (fn[name]{ns-key name}) a-list))))

(defn- add-namespace
  "Given a keyword-keyed map `m`, with non-namespaced keys, prefix its keys
  with the namespace `ns`"
  [m ns]
  (reduce-kv (fn[acc k v]
               (assoc acc (keyword ns (name k)) v))
             {} m))

(defn- ->entry
  "Converts a `BibTexEntry` object `e` into a map with the relevant fields"
  [^BibTeXEntry e]
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
        author-list (process-names base :author)
        editor-list (process-names base :editor)]
    (cond-> base
       true (assoc :type type)
       (some? year) (assoc  :year year)
       (some? author-list) (assoc :author author-list)
       (some? editor-list) (assoc :editor editor-list)
       true (add-namespace "publication"))))



(defn parse-bibliography
  "Parses a file denoted by `file-name` into a collection of maps
  representing the BibTeX entries in the file"
  [file-name]
  (->> file-name
     (java.io.FileReader.)
     ((memfn parse f)  bibtex-parser)
     ((memfn getEntries))
     (map (fn [kv]
            (let [entry-key (.toString(.getKey kv))]
              [entry-key (-> (->entry (.getValue kv))
                            (assoc :publication/key entry-key))])))
     (into {})))

(defn- names->bib
  [k a]
  (let [authors-str (flatten (mapv vals a))]
    (str "\t" k ": {" (string/join " and " authors-str)  "},\n")))

(defmulti field->bib (fn[[k v]]
                      k))


(defmethod field->bib :publication/author [[_ a]]
  (names->bib "author" a))

(defmethod field->bib :publication/editor [[_ a]]
  (names->bib "editor" a))

(defmethod field->bib :publication/title [[k v]]
  (str "\t" (name k) ": {{" v "}},\n"))

(defmethod field->bib :default [[k v]]
  (str "\t" (name k) ": {" v "},\n"))


(defn ->bib [entry]
  (let [#:publication{:keys [key type]} entry
        fields (-> entry
                  (dissoc :publication/key :publication/type :db/id)
                  (->>
                   (reduce (fn[a kv] (conj a (field->bib kv))) [])))]
    (str "@" (name type) "{" key ",\n" (string/join fields) "}")))

(comment

  (def biblio  (parse-bibliography (->> "samples/literature.bib"
                                        io/resource
                                        io/as-file
                                        (#(.getPath %)))))

  biblio
  (def t1 (second (first biblio)))
  (println  (->bib t1))
  )

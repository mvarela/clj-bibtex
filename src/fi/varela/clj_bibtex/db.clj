(ns fi.varela.clj-bibtex.db
  (:require [datascript.core :as d]
            [fi.varela.clj-bibtex.string-distance :as str-d]
            [clojure.string :as string]
            [net.cgrand.xforms :as x]))

(defn make-conn
  "Creates a connection to an empty db with a suitable schema for BibTeX entries"
  []
  (d/conn-from-db
   (d/empty-db {:publication/key {:db/unique :db.unique/identity}
                :publication/title {:db/unique :db.unique/value}
                :publication/author {:db/type :db.type/ref
                                     :db/cardinality :db.cardinality/many}
                :publication/editor {:db/type :db.type/ref
                                     :db/cardinality :db.cardinality/many}
                :author/name {:db/unique :db.unique/identity}
                :editor/name {:db/unique :db.unique/identity}})))

(defn ingest-entry!
  "Adds a `[key entry]` pair coming from [[fi.varela.clj-bibtex/parse-bibliography]]to the databased in `conn`"
  [conn [_ entry]]
  (d/transact! conn [entry]))

(defn ingest-bibliography!
  "Adds the bibliography `biblio` to the database in `conn`. Returns a vector of
  error messages (caused by repeated keys or titles; this might be relaxed later on)"
  [conn biblio]
  (let [errors (atom [])]
    (doseq [entry biblio]
      (try (ingest-entry! conn entry)
           (catch Exception e (swap! errors conj (.getMessage e)))))
    @errors))

(defn all-authors
  "Returns a vector of all authors in `db`"
  [db]
  (d/q '[:find [?n ...]
         :where
         [?e :publication/author ?a]
         [?a :author/name ?n]] db))

(defn all-titles
  "Returns a vector of all titles in `db`"
  [db]
  (d/q '[:find [?t ...]
         :where
         [?e :publication/title ?t]] db))

(defn all-entries
  "Returns a vector of all entries in `db`"
  [db]
  (d/q '[:find [(pull ?e [* {:publication/author [:author/name]
                            :publication/editor [:editor/name]}]) ...]
         :where
         [?e :publication/key]] db))


(defn- build-regex [pattern]
  (let [words (string/split pattern #"\p{Space}")
        p (string/join "\\p{Space}" (map (fn[s]
                                          (str "(?xi)" s))
                                        words))]
    (re-pattern p)))

(defn fuzzy-by-title
  "Returns a vector of entries in `db` whose title matches (case-insensitive)
  `pattern`. Pattern is treated as a regex string"
  [db pattern]
  (let [regex (build-regex pattern)]
    (d/q '[:find [(pull ?e [* {:publication/author [:author/name]
                               :publication/editor [:editor/name]}]) ...]
           :in $ ?p
           :where
           [?e :publication/title ?t]
           [(re-find ?p ?t)]] db regex)))

(defn fuzzy-by-author
  "Returns a vector of entries in `db` where at least an author
  matches (case-insensitive) `pattern`. Pattern is treated as a regex string"
  [db pattern]
  (let [regex (re-pattern (str "(?xi)" pattern))]
    (d/q '[:find [(pull ?e [* {:publication/author [:author/name]
                               :publication/editor [:editor/name]}]) ...]
           :in $ ?p
           :where
           [?e :publication/author ?a]
           [?a :author/name ?n]
           [(re-find ?p ?n)]] db regex)))

(defn- similar-entries
  "Helper function for identifying similar author names or paper titles in `db`, pairwise.
  The type of search to do is specified by `a-key`, which must be in
  `#{:authors :titles}`. The optional `fuzz-level` keyword argument is a value
  in [0,1], indicating the similarity threshold to consider (using Sørensen-Dice
  similarity). The closer to 1 `fuzz-level` is, the more similar the entries
  need to be to be included in the results."
  [db a-key &{:keys [fuzz-level] :or {fuzz-level 0.7}}]
  {:pre [(#{:authors :titles} a-key)]}
  (let [entries (if (= a-key :authors)
                  (all-authors db)
                  (all-titles db))
        xform (comp
               (x/sort)
               (x/partition 2 1)
               (map (fn[[s1 s2]] [s1 s2 (str-d/sorensen-dice s1 s2)]))
               (filter (fn[[_ _ sd]] (> sd fuzz-level))))]
    (into [] xform entries)))


(defn similar-authors
  "Returns a vector of triplets `[a1 a2 score]` for similar author names in `db`, considered pairwise.
   The optional `fuzz-level` keyword argument is a value in [0,1], indicating
  the similarity threshold to consider (using Sørensen-Dice similarity). The
  closer to 1 `fuzz-level` is, the more similar the entries need to be to be
  included in the results.

  ```clojure
  (similar-authors @conn :fuzz-level 0.9)
  ;;=>
  ;;[[\"Heegaard, Poul\" \"Heegaard, Poul E\" 0.9285714285714286]
  ;;[\"Heegaard, Poul E\" \"Heegaard, Poul E.\" 0.967741935483871]
  ;;[\"Kara, Peter A\" \"Kara, Peter A.\" 0.96]
  ;;[\"Liu, Xi\" \"Liu, Xin\" 0.9230769230769231]
  ;;[\"Martini, Maria G\" \"Martini, Maria G.\" 0.9629629629629629]
  ;;[\"Schatz, Raimund\" \"Schatz, Raimund.\" 0.9655172413793104]
  ;;[\"Skorin-Kapov, L.\" \"Skorin-Kapov, Lea\" 0.9032258064516129]
  ;;[\"Yang, Zhe\" \"Yang, Zhen\" 0.9411764705882353]]
  ```"
  [db & {:keys [fuzz-level] :or {fuzz-level 0.7}}]
  (similar-entries db :authors :fuzz-level fuzz-level))

(defn similar-titles
  "Returns a vector of triplets `[t1 t2 score]` for similar titles in `db`, considered pairwise.
   The optional `fuzz-level` keyword argument is a value in [0,1], indicating
  the similarity threshold to consider (using Sørensen-Dice similarity). The
  closer to 1 `fuzz-level` is, the more similar the entries need to be to be
  included in the results.

  ```clojure
  (similar-titles @conn)
  ;;=>
  ;;[[\"Adaptive psychometric scaling for video quality assessment\"
  ;;\"Adaptive testing for video quality assessment\"
  ;;0.8085106382978723]
  ;;[\"OTT-ISP Joint Service Management: A Customer Lifetime Value Based Approach\"
  ;;\"OTT-ISP Joint service management: a customer lifetime value based approach\"
  ;;0.7786259541984732]
  ;;[\"OTT-ISP Joint service management: a customer lifetime value based approach\"
  ;;\"OTT-ISP joint service management: a Customer Lifetime Value based approach \"
  ;;0.8702290076335878]
  ;;[\"Understanding the impact of network dynamics on mobile video user engagement\"
  ;;\"Understanding the impact of video quality on user engagement\"
  ;;0.743801652892562]]
  ```"
  [db & {:keys [fuzz-level] :or {fuzz-level 0.7}}]
  (similar-entries db :titles :fuzz-level fuzz-level))


(comment

  (def conn (make-conn) )

  (ingest-bibliography! conn fi.varela.clj-bibtex.core/biblio)

  (all-authors @conn)

  (all-titles @conn)

  (all-entries @conn)

  (fuzzy-by-author @conn "tobias")

  (fuzzy-by-title @conn "manage")
  (similar-authors @conn :fuzz-level 0.95)

  (similar-titles @conn :fuzz-level 0.85)

)

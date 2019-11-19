(ns fi.varela.bibtex-db
  (:require [datascript.core :as d]))

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

(defn fuzzy-by-title
  "Returns a vector of entries in `db` whose title matches (case-insensitive)
  `pattern`. Pattern is treated as a regex string"
  [db pattern]
  (let [regex (re-pattern (str "(?xi)" pattern))]
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

(comment

  (def conn (make-conn) )

  (ingest-bibliography! conn fi.varela.clj-bibtex/biblio)

  (all-authors @conn)

  (all-titles @conn)

  (all-entries @conn)

  (fuzzy-by-author @conn "tobias")

  (fuzzy-by-title @conn "manage")
)

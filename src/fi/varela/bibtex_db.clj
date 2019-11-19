(ns fi.varela.bibtex-db
  (:require [datascript.core :as d]))

(defn make-conn []
  (d/conn-from-db
   (d/empty-db {:publication/key {:db/unique :db.unique/identity}
                :publication/title {:db/unique :db.unique/value}
                :publication/author {:db/type :db.type/ref
                                     :db/cardinality :db.cardinality/many}
                :publication/editor {:db/type :db.type/ref
                                     :db/cardinality :db.cardinality/many}
                :author/name {:db/unique :db.unique/identity}
                :editor/name {:db/unique :db.unique/identity}})))

(defn ingest-entry! [conn [_ entry]]
  (d/transact! conn [entry]))

(defn ingest-bibliography! [conn biblio]
  (let [errors (atom [])]
    (doseq [entry biblio]
      (try (ingest-entry! conn entry)
           (catch Exception e (swap! errors conj (.getMessage e)))))
    @errors))

(defn all-authors [db]
  (d/q '[:find [?n ...]
         :where
         [?e :publication/author ?a]
         [?a :author/name ?n]] db))

(defn all-titles [db]
  (d/q '[:find [?t ...]
         :where
         [?e :publication/title ?t]] db))

(defn all-entries [db]
  (d/q '[:find [(pull ?e [* {:publication/author [:author/name]
                            :publication/editor [:editor/name]}]) ...]
         :where
         [?e :publication/key]] db))

(defn fuzzy-by-title [db pattern]
  (let [regex (re-pattern (str "(?xi)" pattern))]
    (d/q '[:find [(pull ?e [* {:publication/author [:author/name]
                               :publication/editor [:editor/name]}]) ...]
           :in $ ?p
           :where
           [?e :publication/title ?t]
           [(re-find ?p ?t)]] db regex)))

(defn fuzzy-by-author [db pattern]
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

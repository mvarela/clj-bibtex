(ns fi.varela.bibtex-db
  (:require [datascript.core :as d]))

(def conn (d/conn-from-db
           (d/empty-db {:publication/key {:db/unique :db.unique/identity}
                        :publication/title {:db/unique :db.unique/value}
                        :publication/author {:db/type :db.type/ref
                                             :db/cardinality :db.cardinality/many}
                        :publication/editor {:db/type :db.type/ref
                                             :db/cardinality :db.cardinality/many}
                        :author/name {:db/unique :db.unique/identity}
                        :editor/name {:db/unique :db.unique/identity}})))

(defn add-namespace
  "Given a keyword-keyed map `m`, with non-namespaced keys, prefix its keys
  with the namespace `ns`"
  [m ns]
  (reduce-kv (fn[acc k v]
               (assoc acc (keyword ns (name k)) v))
             {} m))

(defn ingest-entry! [db [entry-key entry]]
  (let [ns-entry (-> (add-namespace entry "publication")
                     (assoc :publication/key entry-key))]
    (d/transact! db [ns-entry])))

(defn ingest-bibliography! [db biblio]
  (let [errors (atom [])]
    (doseq [entry biblio]
      (try (ingest-entry! db entry)
           (catch Exception e (swap! errors conj (.getMessage e)))))
    @errors))

(comment

 (ingest-bibliography! conn fi.varela.clj-bibtex/biblio)

 (d/q '[:find (pull ?e [*])
        :where [?e]] @conn)

 (d/q '[:find (pull ?e [*])
        :where
        [?e :publication/author ?a]
        [?a :author/name ?n]
        [(re-find #"Varela" ?n)]]
      @conn)
 (d/q '[:find ?e ?n
        :where
        [?e :author/name ?n]
        [(re-find #"Tobias" ?n)]] @conn)

 )

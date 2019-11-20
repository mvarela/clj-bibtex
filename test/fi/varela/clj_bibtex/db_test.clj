(ns fi.varela.clj-bibtex.db-test
  (:require [fi.varela.clj-bibtex.db :as sut]
            [fi.varela.clj-bibtex.core :as bib]
            [datascript.core :as d]
            [clojure.test :refer :all]
            [clojure.java.io :as io]))


(def biblio  (bib/parse-bibliography (->> "samples/literature.bib"
                                        io/resource
                                        io/as-file
                                        (#(.getPath %)))))

(def total-entries 239)
(def unique-entries 235)
(def duplicate-entries 4)
;; 98 matches for "^.*title.*QoE.*" in buffer: literature.bib
(def qoe-matches 98)
(def ela-matches 1)
;36 matches for "^.*author.*ho.*feld" in buffer: literature.bib
(def tobias-papers 36)

(def conn (sut/make-conn))

(deftest make-db-conn
  (testing "DB Creation"
    (is (= datascript.db.DB (type @conn)))))

(def errors (sut/ingest-bibliography! conn biblio))

(deftest ingest
  (testing "Populating DB"
    (is (= total-entries (count biblio)))
    (is (= duplicate-entries (count errors)))
    (is (= unique-entries (first (d/q '[:find [(count ?e )]
                             :where
                             [?e :publication/key]] @conn))))))

(deftest all-titles
  (testing "Get all titles"
    (is (= unique-entries (count (sut/all-titles @conn))))))

(deftest all-entries
  (testing "Get all entries"
    (is (= unique-entries (count (sut/all-entries @conn))))))

(deftest fuzzy-title
  (testing "Fuzzy title search"
    (is (= ela-matches (count (sut/fuzzy-by-title @conn "ence level")))
        (= qoe-matches (count (sut/fuzzy-by-title @conn "qoe"))))))

(deftest fuzzy-author
  (testing "Fuzzy author search"
    (is (= tobias-papers (count (sut/fuzzy-by-author @conn "ho.*eld"))))))

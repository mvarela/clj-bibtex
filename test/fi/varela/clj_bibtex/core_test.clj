(ns fi.varela.clj-bibtex.core-test
  (:require [clojure.test :refer :all]
            [fi.varela.clj-bibtex.core :as sut]
            [clojure.java.io :as io]))

(def biblio  (sut/parse-bibliography (->> "samples/literature.bib"
                                        io/resource
                                        io/as-file
                                        (#(.getPath %)))))

(def entry (second (first biblio)))

(defn- check-keys [[_ entry]]
  (every? some? (vals entry)))

(deftest entries-non-nil
  (testing "Bib entries contain no nil values"
    (is (= true (every? check-keys biblio)))))

(deftest keys-non-nil
  (testing "No nil keys"
    (is (= true (every? some? (mapv first biblio))))))

(deftest author-names-in-last-first-format
  (testing "Author names are in \"Last, first\" format")
  (is (= "Last, First" (sut/normalize-author "Last, First")))
  (is (= "Mononym" (sut/normalize-author "Mononym")))
  (is (= "Last, First" (sut/normalize-author "First Last"))))

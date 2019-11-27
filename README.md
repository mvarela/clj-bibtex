
# Table of Contents

1.  [clj-bibtex](#orgddfffbb)
    1.  [Usage](#org3cf067f)
    2.  [Options](#org63c511f)
    3.  [Examples](#orgdd5844f)
        1.  [Bugs](#org0ad52ea)
    4.  [License](#org94fa9a9)


<a id="orgddfffbb"></a>

# clj-bibtex

[https://circleci.com/gh/mvarela/clj-bibtex.svg?style=svg](https://circleci.com/gh/mvarela/clj-bibtex)
[![img](https://img.shields.io/clojars/v/fi.varela/clj-bibtex.svg)](https://clojars.org/fi.varela/clj-bibtex)

A simple wrapper around [jbibtex](https://github.com/jbibtex/jbibtex) for handling BibTeX bibliographies..

`jbibtex` is a nice, robust library, but it is a bit too OO to use with direct
interop. `clj-bibtex` provides a way to load a `.bib` file into plain Clojure
data structures, and optionally into a Datascript database, allowing fuzzy
searches and de-duplication of entries.

The idea is to build on top of this some functionality for managing the
bibliography ()


<a id="org3cf067f"></a>

## Usage

The API is quite basic. It has functionality to parse and serialize
bibliographies (in `fi.varela.clj-bibtex.core`), and to feed them into a
Datascript DB (in `fi.varela.clj-bibtex.db`), from where we can also do
queries, and re-build (normalized) BibTeX output.

The DB API supports fuzzy searches, and finding similar author names or paper
titles, which sometimes can sneak into a .bib file used in several papers.


<a id="org63c511f"></a>

## Options

None! Simple, isn't it? 


<a id="orgdd5844f"></a>

## Examples

If you start a REPL in this project, you can test it with

```clojure
    (in-ns 'user)
    
    (require '[fi.varela.clj-bibtex.core :as b]
             '[fi.varela.clj-bibtex.db :as d]
             '[clojure.java.io :as io]
             '[clojure.string :as string])
    
    (def biblio  (b/parse-bibliography (->> "samples/literature.bib"
                                        io/resource
                                        io/as-file
                                        (#(.getPath %)))))
    
    (def conn (d/make-conn))
    
    (d/ingest-bibliography! conn biblio)
    ;; => ["Cannot add #datascript/Datom [559 :publication/title \"A machine learning approach to classifying YouTube QoE based on encrypted network traffic\" 536871073 true] because of unique constraint: (#datascript/Datom [407 :publication/title \"A machine learning approach to classifying YouTube QoE based on encrypted network traffic\" 536871023 true])" "Cannot add #datascript/Datom [607 :publication/title \"SDNDASH: Improving QoE of HTTP Adaptive Streaming Using Software Defined Networking\" 536871088 true] because of unique constraint: (#datascript/Datom [152 :publication/title \"SDNDASH: Improving QoE of HTTP Adaptive Streaming Using Software Defined Networking\" 536870953 true])" "Cannot add #datascript/Datom [651 :publication/title \"Understanding the impact of video quality on user engagement\" 536871105 true] because of unique constraint: (#datascript/Datom [431 :publication/title \"Understanding the impact of video quality on user engagement\" 536871034 true])" "Cannot add #datascript/Datom [713 :publication/title \"QoE-centric service delivery: A collaborative approach among OTTs and ISPs\" 536871124 true] because of unique constraint: (#datascript/Datom [316 :publication/title \"QoE-centric service delivery: A collaborative approach among OTTs and ISPs\" 536870994 true])"]
    ;; Duplicate entries are not added to the DB, currently
    
    (d/fuzzy-by-author @conn "atzor")
    ;; => [{:publication/publisher "Elsevier",
    ;;   :publication/volume "110",
    ;;   :publication/pages "168–179",
    ;;   :publication/author
    ;;   [#:author{:name "Ahmad, Arslan"}
    ;;    #:author{:name "Floris, Alessandro"}
    ;;    #:author{:name "Atzori, Luigi"}],
    ;;   :publication/type :article,
    ;;   :publication/journal "Computer Networks",
    ;;   :publication/title
    ;;   "QoE-centric service delivery: A collaborative approach among OTTs and ISPs",
    ;;   :db/id 316,
    ;;   :publication/key "ahmad2016qoe",
    ;;   :publication/year 2016}
    ;;  {:publication/pages "1–6",
    ;;   :publication/booktitle
    ;;   "Quality of Multimedia Experience (QoMEX), 2016 Eighth International Conference on",
    ;;   :publication/author
    ;;   [#:author{:name "Ahmad, Arslan"}
    ;;    #:author{:name "Floris, Alessandro"}
    ;;    #:author{:name "Atzori, Luigi"}],
    ;;   :publication/type :inproceedings,
    ;;   :publication/organization "IEEE",
    ;;   :publication/title
    ;;   "QoE-aware service delivery: a joint-venture approach for content and network providers",
    ;;   :db/id 360,
    ;;   :publication/key "ahmad2016qoeQOMEX16",
    ;;   :publication/year 2016}
    ;;    ...
    
    (d/fuzzy-by-title @conn "level ag")
    ;; => [{:publication/pages "1741–1746",
    ;;      :publication/booktitle
    ;;      "2015 IEEE International Conference on Communication Workshop (ICCW)",
    ;;      :publication/author
    ;;      [#:author{:name "Varela, Martn"}
    ;;       #:author{:name "Zwickl, Patrick"}
    ;;       #:author{:name "Schulzrinne, Henning"}
    ;;       #:author{:name "Reichl, Peter"}
    ;;       #:author{:name "Xie, Min"}],
    ;;      :publication/type :inproceedings,
    ;;      :publication/organization "IEEE",
    ;;      :publication/title
    ;;      "From Service Level Agreements (SLA) to Experience Level Agreements (ELA): The Challenges of Selling QoE to the User",
    ;;      :db/id 651,
    ;;      :publication/key "varela2015service",
    ;;      :publication/year 2015}]
    
    (doseq [entry (map b/->bib  (d/fuzzy-by-title @conn "management"))]
      (println entry))
    ;; =>
    ;; @inproceedings{bertozzi2002power,
    ;; 	volume: {2},
    ;; 	pages: {926–930},
    ;; 	booktitle: {Wireless Communications and Networking Conference, 2002. WCNC2002. 2002 IEEE},
    ;; 	author: {Bertozzi, Davide and Benini, Luca and Ricco, Bruno},
    ;; 	organization: {IEEE},
    ;; 	title: {{Power aware network interface management for streaming multimedia}},
    ;; 	year: {2002},
    ;; }
    ;; @article{wang2017data,
    ;; 	publisher: {IEEE},
    ;; 	volume: {24},
    ;; 	pages: {102–110},
    ;; 	author: {Wang, Ying and Li, Peilong and Jiao, Lei and Su, Zhou and Cheng, Nan and Shen, Xuemin Sherman and Zhang, Ping},
    ;; 	journal: {IEEE Wireless Communications},
    ;; 	title: {{A data-driven architecture for personalized QoE management in 5G wireless networks}},
    ;; 	number: {1},
    ;; 	year: {2017},
    ;; }
    ;; @inproceedings{hossfeld2017betas,
    ;; 	pages: {1011–1016},
    ;; 	booktitle: {Integrated Network and Service Management (IM), 2017 IFIP/IEEE Symposium on},
    ;; 	author: {Hoßfeld, Tobias and Fiedler, Markus and Gustafsson, Jörgen},
    ;; 	organization: {IEEE},
    ;; 	title: {{Betas: Deriving quantiles from mos-qos relations of iqx models for qoe management}},
    ;; 	year: {2017},
    ;; }
    ;; @inproceedings{awobuluyi:video-quality,
    ;; 	pages: {1657-1662},
    ;; 	booktitle: {2015 IEEE International Conference on Computer and Information Technology; Ubiquitous Computing and Communications; Dependable, Autonomic and Secure Computing; Pervasive Intelligence and Computing},
    ;; 	author: {Awobuluyi, O. and Nightingale, J. and Wang, Q. and Alcaraz-Calero, J. M.},
    ;; 	title: {{Video Quality in 5G Networks: Context-Aware QoE Management in the SDN Control Plane}},
    ;; 	doi: {10.1109/CIT/IUCC/DASC/PICOM.2015.250},
    ;; 	year: {2015},
    ;; 	month: {Oct},
    ;; }
    ;; ...
    
    
    (similar-titles @conn)
    ;;=>
    ;;[["Adaptive psychometric scaling for video quality assessment"
    ;;"Adaptive testing for video quality assessment"
    ;;0.8085106382978723]
    ;;["OTT-ISP Joint Service Management: A Customer Lifetime Value Based Approach"
    ;;"OTT-ISP Joint service management: a customer lifetime value based approach"
    ;;0.7786259541984732]
    ;;["OTT-ISP Joint service management: a customer lifetime value based approach"
    ;;"OTT-ISP joint service management: a Customer Lifetime Value based approach "
    ;;0.8702290076335878]
    ;;["Understanding the impact of network dynamics on mobile video user engagement"
    ;;"Understanding the impact of video quality on user engagement"
    ;;0.743801652892562]]
    
    (similar-authors @conn :fuzz-level 0.9)
    ;;=>
    ;;[["Heegaard, Poul" "Heegaard, Poul E" 0.9285714285714286]
    ;;["Heegaard, Poul E" "Heegaard, Poul E." 0.967741935483871]
    ;;["Kara, Peter A" "Kara, Peter A." 0.96]
    ;;["Liu, Xi" "Liu, Xin" 0.9230769230769231]
    ;;["Martini, Maria G" "Martini, Maria G." 0.9629629629629629]
    ;;["Schatz, Raimund" "Schatz, Raimund." 0.9655172413793104]
    ;;["Skorin-Kapov, L." "Skorin-Kapov, Lea" 0.9032258064516129]
    ;;["Yang, Zhe" "Yang, Zhen" 0.9411764705882353]]

```

<a id="org0ad52ea"></a>

### Bugs

&#x2026;


<a id="org94fa9a9"></a>

## License

Copyright © 2019 Martín Varela

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.


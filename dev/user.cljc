(ns user
  "Tools for interactive development with the REPL.
   This file should not be included in a production build of the application."
  (:require
   [clojure.spec.alpha :as s]
   [default]
   [dev-util :refer [seed-notes branch-commits first-commit]]
   [notes]
   [util :refer [head]]))

#?(:bb (do
         (require '[babashka.deps :as deps])
         (deps/add-deps '{:deps {org.babashka/spec.alpha {:git/url "https://github.com/babashka/spec.alpha"
                                                          :git/sha "1a841c4cc1d4f6dab7505a98ed2d532dd9d56b78"}}})
         (deps/add-deps '{:deps {orchestra/orchestra {:mvn/version "2021.01.01-1"}}})
         (deps/add-deps '{:deps {djblue/portal {:mvn/version "0.57.3"}}})
         (deps/add-deps '{:deps {vvvvalvalval/scope-capture {:mvn/version "0.3.3"}}})
         (require '[orchestra.spec.test :as st])
         (require '[portal.api :as p])
         (require '[sc.api]))
   :clj (do
          (require '[orchestra.spec.test :as st])
          (require '[portal.api :as p])
          (require '[sc.api])))

(s/fdef ranged-rand
  :args (s/and (s/cat :start int? :end int?)
               #(< (:start %) (:end %)))
  :ret int?
  :fn (s/and #(>= (:ret %) (-> % :args :start))
             #(< (:ret %) (-> % :args :end))))

(defn ranged-rand
  "Returns random int in range start <= rand < end"
  [start end]
  (+ start (long
            (sc.api/spy
             (rand (- end start))))))

(defn plus-two-times-three-minus-1
  [n]
  (- (* 3
        (+ 2 n))
     1))

(comment
  (plus-two-times-three-minus-1 3))

#_{:clj-kondo/ignore [:invalid-arity]} ;; for notes/list (I guess because of (:refer-clojure :exclude [list]))
(comment
  #?(:bb "Babashka" :clj "Clojure" :cljs "ClojureScript" :default "Something else")
  #?(:bb nil :clj (st/instrument))
  (ranged-rand 11 10)
  #?(:bb nil :clj (st/unstrument))
  (ranged-rand 11 10)

  (s/def ::distance (s/cat :amount (s/and number? pos?)
                           :unit #{:meters :miles}))

  (s/conform ::distance [3 :meters])
  (s/explain ::distance [3 :meters])
  (s/conform ::distance [3 :steps])
  (s/explain-data ::distance [3 :steps])

  (def spy-id 2)
  #?(:bb nil :clj (sc.api/ep-info spy-id))

  ;; Option 1: launch Portal as standalone PWA
  (def portal (p/open {:window-title "Portal UI"}))
  ;; Option 2: launch Portal as VS Code tab (requires the djblue.portal VS Code extension)
  (def portal (p/open {:launcher :vs-code
                       :theme :portal.colors/nord}))
  (add-tap #'p/submit)

  (tap> {:foo "bar"})
  (p/clear)

  #?(:bb nil :clj (tap> (sc.api/ep-info spy-id)))

  (def notes-ref "refs/notes/foo")

  (branch-commits)
  (seed-notes {:notes-ref notes-ref})

  (notes/list {:notes-ref notes-ref})
  (notes/list {:notes-ref default/notes-ref})
  (notes/list {:notes-ref "refs/notes/bar"})

  ;; HEAD is the default object to show notes for
  (notes/show {:notes-ref notes-ref})
  (notes/show {:notes-ref notes-ref :ref (head)})

  ;; removing and re-adding a note to the same commit
  (notes/show {:notes-ref notes-ref :ref (first-commit)})
  (notes/remove! {:notes-ref notes-ref :ref (first-commit)})
  (notes/show {:notes-ref notes-ref :ref (first-commit)})
  (notes/add! {:notes-ref notes-ref :ref (first-commit)
               :message "Note about first commit re-added"})
  (notes/show {:notes-ref notes-ref :ref (first-commit)})

  (tap> (with-meta
          [:portal.viewer/html "<div><p>Hello <strong>world</strong></p></div>"]
          {:portal.viewer/default :portal.viewer/hiccup}))

  (p/close portal))

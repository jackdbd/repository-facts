(ns user
  "Tools for interactive development with the REPL.
   This file should not be included in a production build of the application."
  (:require
   [clojure.core :refer [format]]
   [clojure.pprint :refer [pprint]]
   [clojure.string :as str]
   [default]
   [fakes :refer [seed-notes! fake-ci-fact fake-local-fact]]
   [git :refer [head]]
   [notes :refer [tap-note> tap-notes>]]
   [object-facts :as of :refer [tap-fact> tap-facts>]]
   [util :as u]))

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
          (require '[expound.alpha :as expound])
          (require '[orchestra.spec.test :as st])
          (require '[portal.api :as p])
          (require '[clojure.spec.alpha :as s])
          (require '[sc.api])))

;; CRUD operations on simple notes
(comment
  #?(:bb "Babashka" :clj "Clojure" :cljs "ClojureScript" :default "Something else")
  (st/instrument)

  #?(:bb nil :clj (set! s/*explain-out* expound/printer))
  (def notes-ref "refs/notes/foo")
  (def object (last (git/branch-commits)))

  (notes/remove-all! {:notes-ref notes-ref})
  (seed-notes! {:notes-ref notes-ref})

  (notes/list)
  (notes/list {:notes-ref notes-ref})
  (tap-notes> (notes/list {:notes-ref notes-ref}))

  (notes/show)
  (notes/show {:notes-ref notes-ref})
  (notes/show {:notes-ref notes-ref :object (head)})
  (notes/show {:notes-ref notes-ref :object object})

  (def note-sha "93698cfde3d0b05895772b58d062d9076c5c7970")
  (def commit-sha "607b7134d11ac1d0f9f8dc1080f7c2757a086cf4")
  (def line (format "%s %s" note-sha commit-sha))
  (notes/parse-line line)

  (git/branch-commits)
  (def object "57950b36fd195d15bcb8a476abb879238d60347e")

  (notes/remove! {:notes-ref notes-ref :object object})
  (notes/remove! {:notes-ref notes-ref :object object :ignore-missing? true})

  (notes/add! {:notes-ref notes-ref :object commit-sha
               :message "This is a test note."})
  (notes/add! {:notes-ref notes-ref :object commit-sha
               :message "This is the test note overwritten." :force? true})

  (notes/append! {:notes-ref notes-ref :object commit-sha
                  :message "This is appended to a test note."})
  (notes/append! {:notes-ref notes-ref :object commit-sha
                  :no-separator? true
                  :message "This is with no separator."})
  (notes/show {:notes-ref notes-ref :object commit-sha})
  (notes/remove! {:notes-ref notes-ref :object commit-sha})

  (notes/remove-all! {:notes-ref notes-ref})

  (seed-notes! {:notes-ref notes-ref})

  (st/unstrument))

(comment
  #?(:bb "Babashka" :clj "Clojure" :cljs "ClojureScript" :default "Something else")
  (st/instrument)
  (st/unstrument)

  (head)

  (def spy-id 1)
  (sc.api/ep-info spy-id)

  ;; Option 1: launch Portal as standalone PWA
  (def portal (p/open {:window-title "Portal UI"}))
  ;; Option 2: launch Portal as VS Code tab (requires the djblue.portal VS Code extension)
  (def portal (p/open {:launcher :vs-code
                       :theme :portal.colors/nord}))
  (add-tap #'p/submit)

  (tap> {:foo "bar"})
  (p/clear)

  (tap> (sc.api/ep-info spy-id))

  (def notes-ref "refs/notes/foo")
  (git/branch-commits)
  (seed-notes! {:notes-ref notes-ref})

  (notes/list {:notes-ref notes-ref})
  (tap> (notes/list {:notes-ref notes-ref}))
  (notes/list {:notes-ref default/notes-ref})
  (notes/list {:notes-ref "refs/notes/bar"})

  ;; HEAD is the default object to show notes for
  (notes/show {:notes-ref notes-ref})
  (notes/show {:notes-ref notes-ref :object (head)})
  (tap> (notes/show {:notes-ref notes-ref :object (head)}))

  ;; removing and re-adding a note to the same commit
  (def object (-> (git/branch-commits) first))
  (notes/show {:notes-ref notes-ref :object object})
  (notes/remove! {:notes-ref notes-ref :object object})
  (notes/show {:notes-ref notes-ref :object object})
  (notes/add! {:notes-ref notes-ref :object object
               :force? true :message "Note about first commit re-added"})
  (notes/append! {:notes-ref notes-ref :object object
                  :message "Trailing spaces will be stripped       " :stripspace? true})
  (notes/append! {:notes-ref notes-ref :object object
                  :message "Trailing spaces will NOT be stripped       "})
  (notes/show {:notes-ref notes-ref :object object})
  (tap> (notes/show {:notes-ref notes-ref :object object}))

  (def md (str/join "\n" ["# Hello"
                          "\n"
                          "This is a **markdown** note."
                          "\n"
                          "```"
                          "const x = 123"
                          "```"]))
  (notes/add! {:notes-ref notes-ref :object object
               :force? true :message md})
  (u/tap-md-note> {:notes-ref notes-ref :object object})

  (p/close portal))

(comment
  (def portal (p/open {:launcher :vs-code :theme :portal.colors/nord}))
  (add-tap #'p/submit)
  (p/clear)

  (def notes-ref "refs/notes/foo")
  (tap> (format "Note will be stored in / fetched from %s" notes-ref))
  (tap> (seed-notes! {:notes-ref notes-ref}))
  (tap-notes> (notes/list {:notes-ref notes-ref}))

  (tap> {:commits (git/branch-commits)})
  (def object "57950b36fd195d15bcb8a476abb879238d60347e")
  (tap> (format "Note/facts about object %s" object))
  (tap-note> {:notes-ref notes-ref :object object})
  (p/clear)

  (p/close portal))

;; Operations on Facts (JSON Lines)
(comment
  #?(:bb "Babashka" :clj "Clojure" :cljs "ClojureScript" :default "Something else")
  (st/instrument)

  #?(:bb nil :clj (set! s/*explain-out* expound/printer))

  (def portal (p/open {:launcher :vs-code :theme :portal.colors/nord}))
  (add-tap #'p/submit)
  (p/clear)

  (def notes-ref "refs/notes/foo")
  (def object "607b7134d11ac1d0f9f8dc1080f7c2757a086cf4")

  (tap> (notes/remove-all! {:notes-ref notes-ref}))
  (tap> (seed-notes! {:notes-ref notes-ref}))

  (def facts (of/list {:notes-ref notes-ref :object object}))
  (pprint (first facts))
  (tap-facts> {:notes-ref notes-ref :object object})

  (of/append! {:notes-ref notes-ref :object object
               :fact (fake-local-fact)})
  (of/append! {:notes-ref notes-ref :object object
               :fact (fake-ci-fact)})
  (of/append! {:notes-ref notes-ref :object object
               :fact (fake-ci-fact)})
  (tap-facts> {:notes-ref notes-ref :object object})
  (tap-fact> {:notes-ref notes-ref :object object :i 5})

  (p/close portal)
  (st/unstrument))

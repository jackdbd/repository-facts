(ns user
  "Tools for interactive development with the REPL.
   This file should not be included in a production build of the application."
  (:require
   [clojure.string :as str]
   [default]
   [dev-util :refer [seed-notes! branch-commits first-commit tap-note>]]
   [notes]
   [git :refer [head]]))

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

#_{:clj-kondo/ignore [:invalid-arity]} ;; for notes/list (I guess because of (:refer-clojure :exclude [list]))
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
  (branch-commits)
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
  (notes/show {:notes-ref notes-ref :object (first-commit)})
  (notes/remove! {:notes-ref notes-ref :object (first-commit)})
  (notes/show {:notes-ref notes-ref :object (first-commit)})
  (notes/add! {:notes-ref notes-ref :object (first-commit)
               :force? true :message "Note about first commit re-added"})
  (notes/append! {:notes-ref notes-ref :object (first-commit)
                  :message "Trailing spaces will be stripped       " :stripspace? true})
  (notes/append! {:notes-ref notes-ref :object (first-commit)
                  :message "Trailing spaces will NOT be stripped       "})
  (notes/show {:notes-ref notes-ref :object (first-commit)})
  (tap> (notes/show {:notes-ref notes-ref :object (first-commit)}))
  (tap-note> {:notes-ref notes-ref :object (first-commit)})

  (def md (str/join "\n" ["# Hello"
                          "\n"
                          "This is a **markdown** note."
                          "\n"
                          "```"
                          "const x = 123"
                          "```"]))
  (notes/add! {:notes-ref notes-ref :object (first-commit)
               :force? true :message md})
  (tap-note> {:notes-ref notes-ref :object (first-commit)})

  (tap> (with-meta
          [:portal.viewer/html "<div><p>Hello <strong>world</strong></p></div>"]
          {:portal.viewer/default :portal.viewer/hiccup}))

  (p/close portal))

(ns user
  "Tools for interactive development with the REPL.
   This file should not be included in a production build of the application."
  (:require
   [default]
   [dev-util :refer [seed-notes first-commit]]
   [notes]
   [portal.api :as p]
   [util :refer [head]]))

(comment
  ;; Option 1: launch Portal as standalone PWA
  (def portal (p/open {:window-title "Portal UI"}))
  ;; Option 2: launch Portal as VS Code tab (requires the djblue.portal VS Code extension)
  (def portal (p/open {:launcher :vs-code
                       :theme :portal.colors/nord}))

  (add-tap #'p/submit)

  (tap> {:foo "bar"})
  (p/clear)

  (def foo-notes-ref "refs/notes/foo")

  (seed-notes {:notes-ref foo-notes-ref})

  (notes/list {:notes-ref foo-notes-ref})
  (notes/list {:notes-ref default/notes-ref})
  (notes/list {:notes-ref "refs/notes/bar"})

  ;; HEAD is the default object to show notes for
  (notes/show {:notes-ref foo-notes-ref})
  (notes/show {:notes-ref foo-notes-ref
               :ref (head)})

  ;; removing and re-adding a note to the same commit
  (notes/show {:notes-ref foo-notes-ref :ref first-commit})
  (notes/remove! {:notes-ref foo-notes-ref :ref first-commit})
  (notes/show {:notes-ref foo-notes-ref :ref first-commit})
  (notes/add! {:notes-ref foo-notes-ref :ref first-commit
               :message "Note about first commit re-added"})
  (notes/show {:notes-ref foo-notes-ref :ref first-commit})

  (tap> (with-meta
          [:portal.viewer/html "<div><p>Hello <strong>world</strong></p></div>"]
          {:portal.viewer/default :portal.viewer/hiccup}))

  (p/close portal))

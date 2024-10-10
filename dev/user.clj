(ns user
  "Tools for interactive development with the REPL.
   This file should not be included in a production build of the application."
  (:require [portal.api :as p]))

(comment
  ;; Option 1: launch Portal as standalone PWA
  (def portal (p/open {:window-title "Portal UI"}))
  ;; Option 2: launch Portal as VS Code tab (requires the djblue.portal VS Code extension)
  (def portal (p/open {:launcher :vs-code
                       :theme :portal.colors/nord}))

  (add-tap #'p/submit)

  (tap> {:foo "bar"})
  (p/clear)

  (tap> (with-meta
          [:portal.viewer/html "<div><p>Hello <strong>world</strong></p></div>"]
          {:portal.viewer/default :portal.viewer/hiccup}))

  (p/close portal))

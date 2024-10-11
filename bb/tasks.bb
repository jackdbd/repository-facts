(ns tasks
  (:require
   [babashka.classpath :refer [get-classpath split-classpath]]
   [dev-util :as dev]
   [notes]))


(defn list-notes
  [{:keys [notes-ref]}]
  (notes/list {:notes-ref notes-ref}))

(defn print-classpath
  []
  (println "=== CLASSPATH BEGIN ===")
  (doseq [path (set (split-classpath (get-classpath)))]
    (println path))
  (println "=== CLASSPATH END ==="))

(defn seed-notes
  [{:keys [notes-ref]}]
  (dev/seed-notes {:notes-ref notes-ref}))

(ns tasks
  (:require
   [babashka.classpath :refer [get-classpath split-classpath]]
   [babashka.fs :as fs]
   [clojure.pprint :refer [pprint]]
   [default]
   [fakes :refer [fake-facts-as-json-lines-string seed-notes!]]
   [git :refer [head]]
   [notes :as notes]
   [object-facts :as of]
   [taoensso.timbre :refer [info set-level!]]))

(set-level! :debug)

(defn append-fact
  [{:keys [notes-ref object fact]
    :or {notes-ref default/notes-ref object (head)}}]
  (info "Append fact to note" fact)
  (let [result (of/append! {:notes-ref notes-ref
                            :object object 
                            :fact fact})]
    (pprint result)))

(defn list-facts
  [{:keys [notes-ref object]
    :or {notes-ref default/notes-ref object nil}}]
  (let [facts (of/list {:notes-ref notes-ref :object object})]
    (pprint facts)))

(defn list-notes
  [{:keys [notes-ref]}]
  (let [xs (notes/list {:notes-ref notes-ref})]
    (pprint xs)))

(defn print-classpath
  []
  (println "=== CLASSPATH BEGIN ===")
  (doseq [path (set (split-classpath (get-classpath)))]
    (println path))
  (println "=== CLASSPATH END ==="))

(defn remove-notes
  [{:keys [notes-ref]}]
  (notes/remove-all! {:notes-ref notes-ref}))

(defn seed-notes
  [{:keys [notes-ref]}]
  (seed-notes! {:notes-ref notes-ref}))

(defn spit-fake-note
  [{:keys [n-facts]}]
  (let [n n-facts
        fname (format "note-with-%s-facts.jsonl" n)
        fpath (str (fs/path "resources" "fakes" fname))] 
    (spit fpath (fake-facts-as-json-lines-string {:n n}))
    (info (format "Fake git note with %s facts written to %s" n fpath))))

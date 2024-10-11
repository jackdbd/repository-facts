(ns dev-util
  (:require
   [babashka.process :refer [shell]]
   [cheshire.core :as json]
   [clojure.core :refer [format]]
   [clojure.string :as str]
   [default]
   [git :refer [head]]
   [notes]
   [taoensso.timbre :refer [info]])
  (:import
   (java.time LocalDate)))

(defn current-year []
  (.getYear (LocalDate/now)))

(defn yyyy-mm-dd []
  (str (LocalDate/now)))

(defn branch-commits
  "Retrieve a list of commits from the current branch."
  []
  (-> (shell {:out :string} "git rev-list HEAD")
      :out
      str/trim-newline
      str/split-lines))

(defn first-commit [] (last (branch-commits)))
(defn second-commit [] (nth (reverse (branch-commits)) 1))
(defn most-recent-commit [] (first (branch-commits)))
(defn commits [] [(first-commit) (second-commit) (most-recent-commit)])

(def note-as-map {:first_run_at (yyyy-mm-dd)
                  :last_run_at (yyyy-mm-dd)
                  :environments [{"FOO" 123 "BAR" 456 "COLOR" "red" :run_on "GitHub Actions"}
                                 {"FOO" 789 "BAZ" 999 "COLOR" "blue" :run_on "GitHub Actions"}]
                  :diff {:added ["BAZ"]
                         :removed ["BAR"]
                         :modified ["COLOR"]}})

(def note-as-string (json/generate-string note-as-map))

(defn seed-notes! [{:keys [notes-ref]}]
  (info "seed notes in GIT_NOTES_REF" notes-ref)
  (let [xs (commits)]
    (doseq [i (range (count xs))]
      (let [commit (nth xs i)
            th (case i
                 0 "st"
                 1 "nd"
                 2 "rd"
                 "th")]
        (info (format "remove! note to commit %s (%s%s note)" commit (inc i) th))
        (notes/remove! {:notes-ref notes-ref :object commit :ignore-missing? true})

        (info (format "add! note to commit %s (%s%s note)" commit (inc i) th))
        (notes/add! {:notes-ref notes-ref
                     :object commit
                     :message (format "This is the %s%s note added when seeding %s" (inc i) th notes-ref)})))))

(defn tap-note>
  [{:keys [notes-ref object]
    :or {notes-ref default/notes-ref object (head)}}]
  (let [note (:note (notes/show {:notes-ref notes-ref :object object}))]
    (tap> (with-meta
            [:portal.viewer/markdown note]
            {:portal.viewer/default :portal.viewer/hiccup}))))

(comment
  (json/parse-string note-as-string true))
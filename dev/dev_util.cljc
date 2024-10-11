(ns dev-util
  (:require
   [babashka.process :refer [shell]]
   [cheshire.core :as json]
   [clojure.core :refer [format]]
   [clojure.string :as str]
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
(defn third-commit [] (nth (reverse (branch-commits)) 2))
(defn most-recent-commit [] (first (branch-commits)))
(defn commits [] [(first-commit) (second-commit) (third-commit) (most-recent-commit)])

(def note-as-map {:first_run_at (yyyy-mm-dd)
                  :last_run_at (yyyy-mm-dd)
                  :environments [{"FOO" 123 "BAR" 456 "COLOR" "red" :run_on "GitHub Actions"}
                                 {"FOO" 789 "BAZ" 999 "COLOR" "blue" :run_on "GitHub Actions"}]
                  :diff {:added ["BAZ"]
                         :removed ["BAR"]
                         :modified ["COLOR"]}})

(def note-as-string (json/generate-string note-as-map))

(defn seed-notes [{:keys [notes-ref]}]
  (info "seed notes in GIT_NOTES_REF" notes-ref)
  (let [xs (commits)]
    (doseq [i (range (count xs))]
      (let [commit (nth xs i)]
        (info (format "remove! note to commit %s (index %s)" commit i))
        (notes/remove! {:notes-ref notes-ref :ref commit :ignore-missing? true})

        (info (format "add! note to commit %s (index %s)" commit i))
        (notes/add! {:notes-ref notes-ref
                     :ref commit
                     :message (format "Note about commit index %s" i)})))))

(comment
  (json/parse-string note-as-string true))
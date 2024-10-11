(ns dev-util
  (:require
   [cheshire.core :as json]
   [clojure.core :refer [format]]
   [notes]
   [taoensso.timbre :refer [info]])
  (:import
   (java.time LocalDate)))

(defn current-year []
  (.getYear (LocalDate/now)))

(defn yyyy-mm-dd []
  (str (LocalDate/now)))

(def first-commit "607b7134d11ac1d0f9f8dc1080f7c2757a086cf4")
(def second-commit "e95918f917535a8606d9b741e7e5a46d65c35e84")
(def third-commit "7ad0019adb97e393d0ed4be621100d0b118dd71a")
(def commits [first-commit second-commit third-commit])

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
  (notes/remove! {:notes-ref notes-ref :ref first-commit :ignore-missing? true})
  (notes/remove! {:notes-ref notes-ref :ref second-commit :ignore-missing? true})
  (notes/remove! {:notes-ref notes-ref :ref third-commit :ignore-missing? true})

  (doseq [i (range (count commits))]
    (let [commit (nth commits i)]
      (info (format "add! note to commit %s (index %s)" commit i))
      (notes/add! {:notes-ref notes-ref
                   :ref commit
                   :message (format "Note about commit index %s" i)}))))

(comment
  (json/parse-string note-as-string true))
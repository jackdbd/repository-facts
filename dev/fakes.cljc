(ns fakes
  (:require
   [cheshire.core :as json]
   [clojure.core :refer [format]]
   [clojure.string :as str]
   [default]
   [git]
   [notes]
   [taoensso.timbre :refer [debug info]]
   [time-util :refer [now minus]]
   [util :as u]))

(defn fake-ci-fact []
  {:env {"FOO" "whatever"
         "BAR" "more_whatever"}
   :meta {"ran-at" (str (minus {:instant (now) :days 2 :hours 3 :seconds 1800}))
          "ran-on" "GitHub Actions"}
   :secret {"foo" "/path/to/foo/secret"
            "bar" "/path/to/bar/secret"}})

(defn fake-local-fact []
  {:env (u/env)
   :meta {"ran-at" (str (now))
          "ran-on" (u/hostname)}
   :secret {"foo" "/path/to/foo/secret"}})

(defn fake-facts [n]
  (debug "Generate" n "fake facts")
  (map #(if (even? %) (fake-ci-fact) (fake-local-fact))
       (range n)))

(defn fake-facts-as-json-lines-string
  [{:keys [n]}]
  (str/join "\n" (map (fn [m] (json/generate-string m))
                      (fake-facts n))))

(defn seed-notes! [{:keys [notes-ref]}]
  (info "seed notes in GIT_NOTES_REF" notes-ref)
  (let [xs (git/branch-commits)]
    (doseq [i (range (count xs))]
      (let [commit (nth xs i)
            th (case i
                 0 "st"
                 1 "nd"
                 2 "rd"
                 "th")
            n-facts 3]
        (info (format "remove! note from object %s (%s%s note)" commit (inc i) th))
        (notes/remove! {:notes-ref notes-ref :object commit :ignore-missing? true})

        ;; This object is a git annotated tag
        (notes/add! {:notes-ref notes-ref
                     :object "23f90353962dcd0be2c727b04759ddfd6d8de7c9"
                     :message (fake-facts-as-json-lines-string {:n 2})})

        (info (format "add! note to object %s (%s%s note)" commit (inc i) th))
        (notes/add! {:notes-ref notes-ref
                     :object commit
                     :message (fake-facts-as-json-lines-string {:n n-facts})})))
    (format "Seeded %s notes in GIT_NOTES_REF %s" (count xs) notes-ref)))

(comment
  (def n 3)
  (fake-facts n))
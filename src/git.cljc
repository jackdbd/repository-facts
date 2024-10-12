(ns git
  (:require
   [babashka.process :refer [shell]]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]))

(defn sha-1? [s]
  (and (string? s) (= 40 (count s))))

(defn sha-256? [s]
  (and (string? s) (= 64 (count s))))

(defn hash? [s]
  (or (sha-1? s) (sha-256? s)))

(s/def ::object hash?)

(s/fdef head
  :ret ::object)

(defn head
  "Return the commit hash of HEAD."
  []
  (let [cmd "git rev-parse HEAD"
        {:keys [exit out]} (shell {:err :string :out :string} cmd)]
    (if (= 0 exit)
      (str/trim out)
      nil)))

(defn branch-commits
  "Retrieve a list of commits from the current branch."
  []
  (-> (shell {:out :string} "git rev-list HEAD")
      :out
      str/trim-newline
      str/split-lines))

(def notes-ref-regex #"^refs/notes/\w+$")

(defn notes-ref? [s]
  (boolean (re-matches notes-ref-regex s)))

(s/def ::notes-ref notes-ref?)

(comment
  (s/valid? ::object (head))
  (s/valid? ::notes-ref "refs/notes/commits")
  (s/valid? ::notes-ref "refs/notes/foo"))
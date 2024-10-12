(ns object-facts
  (:refer-clojure :exclude [list])
  (:require
   [cheshire.core :as json]
   [clojure.core :refer [format]]
   [clojure.pprint :refer [pprint]]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [default]
   [git :as git :refer [head]]
   [notes :as notes]
   [taoensso.timbre :refer [debug]]))

;; TODO: create a library called "repository-facts" (or "object-facts") that
;; exposes 2 public functions:
;;
;; 1. append! - appends a fact in a git note. The format used for each
;;    note is JSON Lines, so each fact is a JSON string.
;; 2. list - returns a list of facts across all git notes. It parses JSON Lines,
;;    so the user does not have to know this library uses JSON Lines.
;;
;; Having access to all repository facts, the user can decide how to generate a
;; version string. A few examples
;; - Every time the env var FEATURE_TOGGLE changes from one fact to the next one,
;;   we bump major.
;; - Every time the env var USER is jack, we add a pre-release suffix to semver
;;   or we bump it (e.g. 1.2.3-RC.42).

(s/def ::env-key (s/or :string string? :keyword keyword?))
(s/def ::env-val (s/or :string string? :number int?))
(s/def ::env (s/map-of ::env-key ::env-val))
(s/def ::secret-key (s/or :string string? :keyword keyword?))
(s/def ::secret-val (s/or :string string? :number int?))
(s/def ::secret (s/map-of ::secret-key ::secret-val))
(s/def ::meta-key (s/or :string string? :keyword keyword?))
(s/def ::meta-val (s/or :string string? :number int?))
(s/def ::meta (s/map-of ::meta-key ::meta-val))

(s/def ::error string?)
(s/def ::message string?)

;; A git note is a string. This namespace assumes the format of such string is
;; JSON Lines. Let's call "fact" each JSON string in this JSON Lines string.
;; So a git note is a list of facts. Once parsed, a fact should have this schema.
(s/def ::fact (s/keys :req-un [::env ::secret ::meta]))

(comment
  (s/valid? ::env {"FOO" true})
  (s/valid? ::env {"FOO" "bar"})
  (s/valid? ::env {:FOO "bar"})

  (s/valid? ::secret {"foo" true})
  (s/valid? ::secret {"foo" "/path/to/foo/secret"})
  (s/valid? ::secret {:foo "/path/to/foo/secret"})

  (def commit-sha "607b7134d11ac1d0f9f8dc1080f7c2757a086cf4")

  (def john's-env {"HOME" "/home/john"
                   "LOG_LEVEL" "info"
                   "USER" "john"})
  (s/valid? ::env john's-env)

  (def john's-secret {"foo" "/home/john/foo"
                      "v" "/home/john/bar"})
  (s/valid? ::secret john's-secret)

  (s/valid? {} ::fact)
  (s/explain ::fact {})
  (s/valid? ::fact {:env john's-env
                    :secret john's-secret
                    :meta {"some-key" "some-value"}}))

(defn note->facts
  [{:keys [notes/note notes/annotated notes-ref]}]
  (debug (format "Extract note %s (it annotates object %s)" note annotated))
  ;; TODO: what if we can't show the note? Just throw an exception?
  (let [ret (notes/show {:notes-ref notes-ref :object annotated})
        jsonl (:notes/note ret)]
    (str/split-lines jsonl)))

(s/def ::list-config (s/keys :opt-un [::git/notes-ref ::git/object]))

(s/fdef list
  :args (s/alt :nullary (s/cat)
               :unary (s/cat :config ::list-config))
  :ret (s/coll-of ::fact))

(defn list
  ([] (list {}))
  ([{:keys [notes-ref object]
     :or {notes-ref default/notes-ref}}]
   (let [notes (if object
                 (notes/list {:notes-ref notes-ref :object object})
                 (notes/list {:notes-ref notes-ref}))
         strings (mapcat #(note->facts (assoc % :notes-ref notes-ref)) notes)
         facts (map #(json/parse-string % true) strings)]
     (debug (format "Found a total of %s facts across %s notes" (count facts) (count notes)))
     facts)))

(s/def ::append!-config (s/keys :req-un [::fact]
                                :opt-un [::git/notes-ref ::git/object]))

(s/def ::append!-ret (s/or :success (s/keys :req [::notes/message])
                           :failure (s/keys :req [::notes/error])))

(s/fdef append!
  :args (s/cat :config ::append!-config)
  :ret ::append!-ret)

(defn append!
  [{:keys [fact
           notes-ref
           object]
    :or {notes-ref default/notes-ref object (head)}}]
  (let [message (json/generate-string fact)
         ;; Adding a single newline to each message is important!
         ;; We can't use the default separator defined by the `git notes append`
         ;; CLI because it is 2 newlines and it would mess up JSON Lines.
         ;; Passing a custom separator seems NOT to work, so the only way to
         ;; make it work is to add a newline at the end of the message and avoid
         ;; using a separator altogether.
        result (notes/append! {:notes-ref notes-ref
                               :object object
                               :message (str message "\n")
                               :no-separator? true})]
    (debug "Append fact about object" object "in" notes-ref fact)
    result))

(defn tap-fact>
  [{:keys [notes-ref object i]
    :or {notes-ref default/notes-ref
         object (head)
         i 0}}]
  (let [result (notes/show {:notes-ref notes-ref :object object})
        note-as-string (:notes/note result)
        lines (str/split-lines note-as-string)
        fact (json/parse-string (nth lines i) true)
        label (format "Fact %s (of %s) on object %s" (inc i) (count lines) object)]
    (tap> (with-meta
            [label fact]
            {:notes-ref notes-ref
             :portal.viewer/default :portal.viewer/inspector
             :portal.viewer/inspector {:expanded 3}}))))

(defn tap-facts>
  [{:keys [notes-ref object]
    :or {notes-ref default/notes-ref object (head)}}]
  (let [result (notes/show {:notes-ref notes-ref :object object})
        note-as-string (:notes/note result)
        lines (str/split-lines note-as-string)
        facts (map #(json/parse-string % true) lines)
        label (format "%s facts about object %s" (count facts) object)]
    (tap> (with-meta
            [label facts]
            {:portal.viewer/default :portal.viewer/inspector
             :portal.viewer/inspector {:expanded 10}}))))

(comment
  (def notes-ref "refs/notes/foo")
  (def object "607b7134d11ac1d0f9f8dc1080f7c2757a086cf4")

  (list)
  (list {:notes-ref notes-ref})

  (s/valid? ::append!-config {})
  (s/explain ::append!-config {})
  (s/explain ::append!-config {::fact {}})
  (s/explain ::append!-config {::fact {:env john's-env
                                       :secret john's-secret
                                       :meta {"some-key" "some-value"}}})
  (append! {:notes-ref notes-ref
            :object object
            :fact {:env john's-env
                   :secret john's-secret
                   :meta {"some-key" "some-value"}}}))
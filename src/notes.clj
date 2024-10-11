(ns notes
  (:refer-clojure :exclude [list ref])
  (:require
   [babashka.process :refer [shell]]
   [cheshire.core :as json]
   [clojure.core :refer [format]]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [default]
   [git :as git :refer [head]]
   [taoensso.timbre :refer [debug warn]])
  (:import (java.lang Exception)))

;; TODO: git notes copy
;; TODO: git notes edit
;; TODO: git notes prune --dry-run --verbose

(s/def ::error string?)
(s/def ::force? boolean?)
(s/def ::ignore-missing? boolean?)
(s/def ::message string?)
(s/def ::parse? boolean?)
(s/def ::separator string?)
(s/def ::stripspace? boolean?)

(s/def ::line
  (s/and string?
         #(let [parts (str/split % #" ")]
            (and (= 2 (count parts))
                 (every? git/hash? parts)))))

(s/fdef line->note-map
  :args (s/cat :line ::line)
  :ret (s/keys :req-un [::note ::annotated]))

(defn- line->note-map
  "Convert a space-separated string into a map containing the hash of the note
   object and the hash of the annotated object (e.g. a git commit)."
  [s]
  (let [splits (str/split s #" ")]
    {:note (first splits)
     :annotated (second splits)}))

(comment
  (def line "93698cfde3d0b05895772b58d062d9076c5c7970 607b7134d11ac1d0f9f8dc1080f7c2757a086cf4")
  (s/valid? ::line line)
  (line->note-map line))

(s/def ::list-config (s/keys :opt-un [::git/notes-ref ::git/object]))

(comment
  (s/valid? ::list-config {:object "93698cfde3d0b05895772b58d062d9076c5c7970"})
  (s/valid? ::list-config {:object "93698cfde3d0b05895772b58d062d9076c5c7970"
                           :notes-ref "refs/notes/foo"}))

(s/fdef list
  :args (s/cat :config ::list-config)
  :ret (s/coll-of (s/keys :req-un [::note ::annotated])))

(defn list
  "List the notes object for a given object. If no object is given, show a list
   of all note objects and the objects they annotate."
  ([]
   (list {}))
  ([{:keys [object
            notes-ref]
     :or {notes-ref default/notes-ref}}]
   (let [cmd (if (nil? object)
               (format "git notes list")
               (format "git notes list %s" object))
         result (try (shell {:extra-env {"GIT_NOTES_REF" notes-ref}
                             :err :string
                             :out :string} cmd)
                     (catch Exception _ex
                       (let [causes (if object #{:no-note} #{:no-notes})
                             msg (if object
                                   (format "object %s has no note in GIT_NOTES_REF %s (exception)" object notes-ref)
                                   (format "no notes in GIT_NOTES_REF %s (exception)" notes-ref))
                             exc (ex-info msg
                                          {:causes causes
                                           :object object
                                           :notes-ref notes-ref})
                             err-data (ex-data exc)
                             err-msg (format "Cannot show note: %s" (ex-message exc))]
                         (debug (json/generate-string err-data))
                         {:err err-msg :out ""})))
         _err (str/trim-newline (:err result))
         out (str/trim-newline (:out result))]
     #_(debug (json/generate-string {:out out :err err}))
     (if (empty? out)
       (let [msg (if object
                   (format "object %s has no note in GIT_NOTES_REF %s" object notes-ref)
                   (format "no notes in GIT_NOTES_REF %s" notes-ref))]
         (debug msg)
         '())
       (let [s (if object (format "%s %s" out object) out)
             lines (str/split s #"\n")
             msg (if object
                   (do (assert (= 1 (count lines))
                               (format "Found %s notes for object %s. In Git, each object (e.g., a commit, blob, tree) can have at most one note in a single note namespace." (count lines) object))
                       (format "object %s has a note in %s" object notes-ref))
                   (format "%s notes in %s" (count lines) notes-ref))]
         (debug msg)
         (map line->note-map lines))))))

(comment
  (def notes-ref "refs/notes/foo")
  (def commit-not-annotated "e95918f917535a8606d9b741e7e5a46d65c35e84")
  (def commit-annotated-in-commits-and-foo "607b7134d11ac1d0f9f8dc1080f7c2757a086cf4")
  (def commit-annotated-just-in-foo "b2a1c31124d6aeba150c62285d6aed16a6067d18")

  (list)
  (list {:notes-ref notes-ref})
  (list {:notes-ref "refs/notes/nonexistent-notes-ref"})

  (list {:object commit-not-annotated})
  (list {:object commit-annotated-just-in-foo :notes-ref notes-ref}))

(s/def ::show-config (s/keys :opt-un [::git/notes-ref ::parse? ::git/object]))

(comment
  (s/valid? ::show-config {:notes-ref "refs/notes/foo"
                           :object "93698cfde3d0b05895772b58d062d9076c5c7970"
                           :parse? true}))

(s/fdef show
  :args (s/cat :config ::show-config)
  :ret (s/keys :opt [::error ::note]))

(defn show
  "Show the notes for a given object (defaults to HEAD)."
  ([] (show {}))
  ([{:keys [object
            notes-ref
            parse?]
     :or {object (head)
          notes-ref default/notes-ref
          parse? default/parse?}}]
   (let [cmd (format "git notes show %s" object)
         result (try (shell
                      {:extra-env {"GIT_NOTES_REF" notes-ref}
                       :err :string
                       :out :string} cmd)
                     (catch Exception _ex
                       (let [causes #{:no-note}
                             msg (format "Cannot show note: object %s has no note in GIT_NOTES_REF %s" object notes-ref)
                             exc (ex-info msg {:causes causes
                                               :object object
                                               :notes-ref notes-ref
                                               :parse? parse?})
                             err-data (ex-data exc)]
                         (warn (json/generate-string err-data))
                         {:err msg :out ""})))
         err (str/trim-newline (:err result))
         out (str/trim-newline (:out result))]
     #_(debug (json/generate-string {:out out :err err}))
     (if (empty? err)
       (if parse?
         {:note (json/parse-string out true)}
         {:note out})
       {:error err}))))

(s/def ::remove!-config (s/keys :opt-un [::ignore-missing? ::git/notes-ref ::git/object]))

(comment
  (s/valid? ::remove!-config {:notes-ref "refs/notes/foo"
                              :object "93698cfde3d0b05895772b58d062d9076c5c7970"
                              :ignore-missing? true}))

(s/fdef remove!
  :args (s/cat :config ::remove!-config)
  :ret (s/keys :opt [::error ::message]))

(defn remove!
  ([] (remove! {}))
  ([{:keys [object
            notes-ref
            ignore-missing?]
     :or {object (head)
          notes-ref default/notes-ref
          ignore-missing? default/ignore-missing?}}]
   (let [cmd (format "git notes remove %s" object)
         result (try (shell
                      {:extra-env {"GIT_NOTES_REF" notes-ref}
                       :err :string
                       :out :string} cmd)
                     (catch Exception _ex
                       (if ignore-missing?
                         {:err "" :out (format "Cannot remove: object %s has no note, but ignore-missing? was set to true" object)}
                         (let [causes #{:no-note :no-ignore-missing}
                               msg (format "Cannot remove: object %s has no note" object)
                               exc (ex-info msg {:causes causes
                                                 :object object
                                                 :notes-ref notes-ref
                                                 :ignore-missing? ignore-missing?})
                                ;; msg (format "Cannot remove note: %s" (ex-message exc))
                               err-data (ex-data exc)]
                           (warn (json/generate-string err-data))
                           {:err msg :out "" :errored true}))))
         err (str/trim-newline (:err result))
         out (str/trim-newline (:out result))] ;; :out seems always empty
     (debug (json/generate-string {:out out :err err}))
     (if (:errored result)
       {:error err}
       {:message err}))))

(comment
  (def notes-ref "refs/notes/foo")
  (def commit-not-annotated "e95918f917535a8606d9b741e7e5a46d65c35e84")
  (def commit-annotated-in-commits-and-foo "607b7134d11ac1d0f9f8dc1080f7c2757a086cf4")
  (def commit-annotated-just-in-foo "b2a1c31124d6aeba150c62285d6aed16a6067d18")

  (remove! {:object commit-not-annotated})
  (remove! {:object commit-not-annotated :ignore-missing? true})
  (remove! {:object commit-annotated-in-commits-and-foo
            :notes-ref default/notes-ref}))

(s/def ::add!-config (s/keys :req-un [::git/object ::message]
                             :opt-un [::force? ::git/notes-ref]))

(comment
  (s/valid? ::add!-config {:notes-ref "refs/notes/foo"
                           :object "e95918f917535a8606d9b741e7e5a46d65c35e84"
                           :message "bar" :force? true}))

(s/fdef add!
  :args (s/cat :config ::add!-config)
  :ret (s/keys :opt [::error ::message]))

(defn add!
  [{:keys [force?
           object
           message
           notes-ref]
    :or {force? default/force?
         notes-ref default/notes-ref}}]
  (let [cmd (if force?
              (format "git notes add --force --message '%s' %s" message object)
              (format "git notes add --message '%s' %s" message object))
        result (try (debug cmd)
                    (shell {:extra-env {"GIT_NOTES_REF" notes-ref}
                            :err :string
                            :out :string} cmd)
                    (catch Exception _ex
                      (let [causes #{:note-exists :no-force}
                            ;; msg (.getMessage ^Throwable ex)
                            msg (format "Cannot add note: object %s already has a note in GIT_NOTES_REF %s. If you want to overwrite an existing note, set force? to true" object notes-ref)
                            exc (ex-info msg {:causes causes
                                              :object object
                                              :notes-ref notes-ref
                                              :force? force?})
                            err-data (ex-data exc)]
                        (warn (json/generate-string err-data))
                        {:err msg :out ""})))
        err (str/trim-newline (:err result))
        _out (str/trim-newline (:out result))]
    #_(debug (json/generate-string {:out out :err err}))
    (if (empty? err)
      {:message (format "Added note to object %s in GIT_NOTES_REF %s" object notes-ref)}
      {:error err})))

(s/def ::append!-config (s/keys :req-un [::git/object ::message]
                                :opt-un [::git/notes-ref ::separator ::stripspace?]))

(comment
  (s/explain ::append!-config {:notes-ref "refs/notes/foo"
                               :object "e95918f917535a8606d9b741e7e5a46d65c35e84"
                               :message "foo"
                               :separator "\n"
                               :stripspace? true}))

(s/fdef append!
  :args (s/cat :config ::append!-config)
  :ret (s/keys :opt [::error ::message]))

(defn append!
  [{:keys [object
           message
           notes-ref
           stripspace?]
    :or {notes-ref default/notes-ref
         stripspace? default/stripspace?}}]
  (let [cmd (if stripspace?
              (format "git notes append --message '%s' --stripspace %s" message object)
              (format "git notes append --message '%s' --no-stripspace %s" message object))
        result (try (debug cmd)
                    (shell {:extra-env {"GIT_NOTES_REF" notes-ref}
                            :err :string
                            :out :string} cmd)
                    (catch Exception ex
                      (let [causes #{:todo}
                            msg (.getMessage ^Throwable ex)
                            ;; msg (format "Cannot append note: object %s already has a note in GIT_NOTES_REF %s. If you want to overwrite an existing note, set force? to true" object notes-ref)
                            exc (ex-info msg {:causes causes
                                              :object object
                                              :notes-ref notes-ref})
                            err-data (ex-data exc)]
                        (warn (json/generate-string err-data))
                        {:err msg :out ""})))
        err (str/trim-newline (:err result))
        out (str/trim-newline (:out result))]
    (debug (json/generate-string {:out out :err err}))
    (if (empty? err)
      {:message (format "Appended note to object %s in GIT_NOTES_REF %s" object notes-ref)}
      {:error err})))
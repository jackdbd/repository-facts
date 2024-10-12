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
   [taoensso.timbre :refer [debug]])
  (:import (java.lang Exception)))

;; TODO: git notes copy
;; TODO: git notes edit
;; TODO: git notes prune --dry-run --verbose

(s/def ::error string?)
(s/def ::force? boolean?)
(s/def ::ignore-missing? boolean?)
(s/def ::message string?)
(s/def ::separator string?)
(s/def ::stripspace? boolean?)

(def git-env
  {"GIT_AUTHOR_EMAIL" (:email default/user)
   "GIT_AUTHOR_NAME" (:name default/user)
   "GIT_COMMITTER_EMAIL" (:email default/user)
   "GIT_COMMITTER_NAME" (:name default/user)})

(s/def ::show-config (s/keys :opt-un [::git/notes-ref ::git/object]))
(s/def ::show-ret (s/or :success (s/keys :req [::note])
                        :failure (s/keys :req [::error])))

(s/fdef show
  :args (s/alt :nullary (s/cat)
               :unary (s/cat :config ::show-config))
  :ret ::show-ret)

(defn show
  "Show all git notes for a given object (defaults to HEAD)."
  ([] (show {}))
  ([{:keys [object
            notes-ref]
     :or {object (head)
          notes-ref default/notes-ref}}]
   (let [cmd (format "git notes show %s" object)
         result (try (shell {:extra-env (merge git-env {"GIT_NOTES_REF" notes-ref})
                             :err :string :out :string} cmd)
                     (catch Exception _ex
                       (let [causes #{:no-note}
                             msg (format "Cannot show note: object %s has no note in GIT_NOTES_REF %s" object notes-ref)
                             _exc (ex-info msg {:causes causes
                                                :object object
                                                :notes-ref notes-ref})]
                         ;; (ex-data exc)
                         {:err msg :out ""})))
         err (str/trim-newline (:err result))
         out (str/trim-newline (:out result))]
     (if (empty? err)
       {::note out}
       {::error err}))))

(comment
  (def notes-ref "refs/notes/foo")
  (def object "607b7134d11ac1d0f9f8dc1080f7c2757a086cf4")
  (s/valid? ::show-config {})
  (s/valid? ::show-config {:notes-ref notes-ref})
  (s/valid? ::show-config {:notes-ref notes-ref :object object})
  (s/valid? ::show-ret {})
  (s/explain ::show-ret {})
  (s/valid? ::show-ret {::error "foo"})
  (s/valid? ::show-ret (show)))

(defn two-space-separated-git-hashes? [x]
  (let [parts (str/split x #" ")]
    (and (= 2 (count parts))
         (every? git/hash? parts))))

(s/def ::line (s/and string? two-space-separated-git-hashes?))

(s/fdef parse-line
  :args (s/cat :line ::line)
  :ret (s/keys :req [::note ::annotated]))

(defn parse-line
  "Convert a space-separated string into a map containing the hash of the note
   object and the hash of the annotated object (e.g. a git commit)."
  [s]
  (let [splits (str/split s #" ")
        note (first splits)
        object (second splits)]
    (debug (format "%s (note) => %s (annotated object)" note object))
    {::note note ::annotated object}))

(comment
  (def note-sha "93698cfde3d0b05895772b58d062d9076c5c7970")
  (def commit-sha "607b7134d11ac1d0f9f8dc1080f7c2757a086cf4")
  (def line (format "%s %s" note-sha commit-sha))
  (s/valid? ::line note-sha)
  (s/explain ::line note-sha)
  (s/valid? ::line line)
  (parse-line line))

(s/def ::list-config (s/keys :opt-un [::git/notes-ref ::git/object]))

(comment
  (s/valid? ::list-config {:object "93698cfde3d0b05895772b58d062d9076c5c7970"})
  (s/valid? ::list-config {:object "93698cfde3d0b05895772b58d062d9076c5c7970"
                           :notes-ref "refs/notes/foo"}))

(s/fdef list
  :args (s/alt :nullary (s/cat)
               :unary (s/cat :config ::list-config))
  :ret (s/coll-of (s/keys :req [::note ::annotated])))

(defn list
  "List all notes object for a given object. If no object is given, show a list
   of all note objects and the objects they annotate."
  ([]
   (list {}))
  ([{:keys [object
            notes-ref]
     :or {notes-ref default/notes-ref}}]
   (let [cmd (if (nil? object)
               (format "git notes list")
               (format "git notes list %s" object))
         result (try (shell {:extra-env (merge git-env {"GIT_NOTES_REF" notes-ref})
                             :err :string :out :string} cmd)
                     (catch Exception _ex
                       (let [causes (if object #{:no-note} #{:no-notes})
                             msg (if object
                                   (format "found no notes for object %s in GIT_NOTES_REF %s (exception)" object notes-ref)
                                   (format "found no notes in GIT_NOTES_REF %s (exception)" notes-ref))
                             exc (ex-info msg
                                          {:causes causes
                                           :object object
                                           :notes-ref notes-ref})
                             err-msg (format "Cannot list notes: %s" (ex-message exc))]
                         {:err err-msg :out ""})))
         _err (str/trim-newline (:err result))
         out (str/trim-newline (:out result))]
     (if (empty? out)
       (let [msg (if object
                   (format "Found no note for object %s in GIT_NOTES_REF %s" object notes-ref)
                   (format "Found no notes in GIT_NOTES_REF %s" notes-ref))]
         (debug msg)
         '())
       (let [s (if object (format "%s %s" out object) out)
             lines (str/split s #"\n")
             msg (if object
                   (do (assert (= 1 (count lines))
                               (format "Found %s notes for object %s. In Git, each object (e.g., a commit, blob, tree) can have at most one note in a single note namespace." (count lines) object))
                       (format "Found note for object %s in GIT_NOTES_REF %s" object notes-ref))
                   (format "Found %s notes in GIT_NOTES_REF %s" (count lines) notes-ref))]
         (debug msg)
         (map parse-line lines))))))

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

(s/def ::remove!-config (s/keys :opt-un [::ignore-missing? ::git/notes-ref ::git/object]))
(s/def ::remove!-ret (s/or :success (s/keys :req [::message])
                           :failure (s/keys :req [::error])))

(s/fdef remove!
  :args (s/alt :nullary (s/cat)
               :unary (s/cat :config ::remove!-config))
  :ret ::remove!-ret)

(defn remove!
  ([] (remove! {}))
  ([{:keys [object
            notes-ref
            ignore-missing?]
     :or {object (head)
          notes-ref default/notes-ref
          ignore-missing? default/ignore-missing?}}]
   ;; It seems `git notes remove` always prints to stderr, even when there is a
   ;; note to be removed from the object. The only time we get an exception is
   ;; when --ignore-missing is either false or not set.
   (let [cmd (format "git notes remove %s" object)
         result (try (shell {:extra-env (merge git-env {"GIT_NOTES_REF" notes-ref})
                             :err :string :out :string} cmd)
                     (catch Exception _ex
                       ;; (debug (.getMessage ^Throwable ex))
                       (if ignore-missing?
                         {::message (format "Did not remove any note: object %s has no note. Didn't error out, since ignore-missing? was set to true" object)}
                         (let [causes #{:no-note :no-ignore-missing}
                               msg (format "Did not remove any note: object %s has no note. If you want to avoid this error, set ignore-missing? to true" object)
                               _exc (ex-info msg {:causes causes
                                                  :object object
                                                  :notes-ref notes-ref
                                                  :ignore-missing? ignore-missing?})]
                           {::error msg}))))]
     ;; When successful, `git notes list` prints nothing to stdout and a success
     ;; message to stderr. Weird...
     (if (= "" (:out result))
       {::message (:err result)}
       result))))

(comment
  (def notes-ref "refs/notes/foo")
  (def object "607b7134d11ac1d0f9f8dc1080f7c2757a086cf4")

  (s/valid? ::remove!-config {})
  (s/valid? ::remove!-config {:notes-ref notes-ref})
  (s/valid? ::remove!-config {:notes-ref notes-ref :object object})
  (s/valid? ::remove!-config {:notes-ref notes-ref :object object
                              :ignore-missing? true})
  (s/valid? ::remove!-ret {})
  (s/explain ::remove!-ret {})
  (s/explain ::remove!-ret {:error "foo"})
  (s/explain ::remove!-ret {::error "foo"})
  (s/explain ::remove!-ret {:message "foo"})
  (s/explain ::remove!-ret {::message "foo"}))

(s/def ::remove-all!-config (s/keys :req-un [::git/notes-ref]))
(s/def ::remove-all!-ret (s/or :success (s/keys :req [::message])
                               :failure (s/keys :req [::error])))

(s/fdef remove-all!
  :args (s/alt :nullary (s/cat)
               :unary (s/cat :config ::remove-all!-config))
  :ret ::remove-all!-ret)

(defn remove-all!
  ([] (remove-all! {}))
  ([{:keys [notes-ref]
     :or {notes-ref default/notes-ref}}]
   (let [xs (list {:notes-ref notes-ref})
         results (map (fn [{:keys [notes/note notes/annotated]}]
                        (debug "Remove note" note "from object" annotated)
                        (remove! {:object annotated
                                  :notes-ref notes-ref
                                  :ignore-missing? true}))
                      xs)
         not-removed (filter #(::error %) results)
         removed (filter #(::message %) results)
         msg (format "Notes in %s: %s initial notes, %s removed, %s not removed"
                     notes-ref (count xs) (count removed) (count not-removed))]
     (debug msg)
     {::message msg})))

(comment
  (def notes-ref "refs/notes/foo")

  (s/valid? ::remove-all!-config {})
  (s/explain ::remove-all!-config {})
  (s/explain ::remove-all!-config {:notes-ref notes-ref})

  (s/explain ::remove-all!-ret {::error "foo"})
  (s/explain ::remove-all!-ret {::message "foo"}))

(s/def ::add!-config (s/keys :req-un [::git/object ::message]
                             :opt-un [::force? ::git/notes-ref]))

(s/def ::add!-ret (s/or :success (s/keys :req [::message])
                        :failure (s/keys :req [::error])))

(s/fdef add!
  :args (s/cat :config ::add!-config)
  :ret ::add!-ret)

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
                    (shell {:extra-env (merge git-env {"GIT_NOTES_REF" notes-ref})
                            :err :string :out :string} cmd)
                    (catch Exception _ex
                      (if force?
                        {::message (format "Overwrote note to object %s in GIT_NOTES_REF %s" object notes-ref)}
                        (let [causes #{:note-exists :no-force}
                                                    ;; msg (.getMessage ^Throwable ex)
                              msg (format "Cannot add note: object %s already has a note in GIT_NOTES_REF %s. If you want to overwrite an existing note, set force? to true" object notes-ref)
                              _exc (ex-info msg {:causes causes
                                                 :object object
                                                 :notes-ref notes-ref
                                                 :force? force?})]
                          {::error msg}))))]
    ;; When it successfully overwrites a note, `git notes add` prints nothing to
    ;; stdout, and a message to stderr. Since we can return a more descriptive
    ;; message, we just ignore what `git notes add` prints to stderr.
    (if (:err result)
      {::message (format "Overwrote note to object %s in GIT_NOTES_REF %s" object notes-ref)}
      result)))

(comment
  (def notes-ref "refs/notes/foo")
  (def object "607b7134d11ac1d0f9f8dc1080f7c2757a086cf4")

  (s/valid? ::add!-config {})
  (s/explain ::add!-config {})
  (s/explain ::add!-config {:object object :message "foo"})

  (s/valid? ::add!-ret {})
  (s/explain ::add!-ret {})
  (s/explain ::add!-ret {:error "foo"})
  (s/explain ::add!-ret {::error "foo"})
  (s/explain ::add!-ret {:message "foo"})
  (s/explain ::add!-ret {::message "foo"}))

(s/def ::append!-config (s/keys :req-un [::git/object ::message]
                                :opt-un [::git/notes-ref ::separator ::stripspace?]))

(s/def ::append!-ret (s/or :success (s/keys :req [::message])
                           :failure (s/keys :req [::error])))

(s/fdef append!
  :args (s/cat :config ::append!-config)
  :ret ::append!-ret)

(defn append!
  [{:keys [object
           message
           notes-ref
           separator
           no-separator?
           stripspace?]
    :or {notes-ref default/notes-ref
         separator default/separator
         no-separator? default/no-separator?
         stripspace? default/stripspace?}}]
  ;; A custom separator seems to not work... 
  (let [cmd (cond-> (format "git notes append --message '%s'" message)
              true (str " --no-allow-empty")
              (not (nil? separator)) (str (format " --separator %s" separator))
              no-separator? (str " --no-separator")
              stripspace? (str " --stripspace")
              (not stripspace?) (str " --no-stripspace")
              true (str " " object))
        result (try (debug cmd)
                    (shell {:extra-env (merge git-env {"GIT_NOTES_REF" notes-ref})
                            :err :string
                            :out :string} cmd)
                    (catch Exception ex
                      (let [causes #{:todo}
                            msg (.getMessage ^Throwable ex)
                            _exc (ex-info msg {:causes causes
                                               :object object
                                               :notes-ref notes-ref})]
                        {::error msg})))]
    (if (::error result)
      {::error (::error result)}
      {::message (format "Appended message in note to object %s in GIT_NOTES_REF %s" object notes-ref)})))

(comment
  (def notes-ref "refs/notes/foo")
  (def object "607b7134d11ac1d0f9f8dc1080f7c2757a086cf4")

  (s/valid? ::append!-config {})
  (s/explain ::append!-config {})
  (s/valid? ::append!-config {:object object :message "foo"})

  (s/valid? ::append!-ret {})
  (s/explain ::append!-ret {})
  (s/explain ::append!-ret {:error "foo"})
  (s/explain ::append!-ret {::error "foo"})
  (s/explain ::append!-ret {:message "foo"})
  (s/explain ::append!-ret {::message "foo"}))

(defn tap-note>
  [{:keys [notes-ref object]
    :or {notes-ref default/notes-ref object (head)}}]
  (let [result (notes/show {:notes-ref notes-ref :object object})
        note-as-string (:notes/note result)
        lines (str/split-lines note-as-string)
        facts (map #(json/parse-string % true) lines)]
    (tap> (with-meta
            [:notes-ref notes-ref
             :object object
             "JSON Lines" lines
             :facts facts]
            {:portal.viewer/default :portal.viewer/tree}))))

(defn tap-notes>
  [xs]
  (tap> (with-meta
          xs
          {::total-notes (count xs)
           :portal.viewer/default :portal.viewer/table
           :portal.viewer/table {:columns [:notes/note :notes/annotated]}})))

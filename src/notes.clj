(ns notes
  (:refer-clojure :exclude [list ref])
  (:require
   [babashka.process :refer [shell]]
   [cheshire.core :as json]
   [clojure.core :refer [format]]
   [clojure.string :as str]
   [default]
   [taoensso.timbre :refer [debug warn]]
   [util :refer [head]])
  (:import (java.lang Exception)))

;; TODO: git notes append
;; TODO: git notes copy
;; TODO: git notes edit
;; TODO: git notes prune --dry-run --verbose

(defn- line->note-map
  "Convert a space-separated string into a map containing the hash of the note
   object and the hash of the annotated object (e.g. a git commit)."
  [s]
  (let [splits (str/split s #" ")]
    {:note (first splits)
     :annotated (second splits)}))

(comment
  (def line "93698cfde3d0b05895772b58d062d9076c5c7970 607b7134d11ac1d0f9f8dc1080f7c2757a086cf4")
  (line->note-map line))

(defn list
  "List the notes object for a given object. If no object is given, show a list
   of all note objects and the objects they annotate."
  ([]
   (list {}))
  ([{:keys [ref
            notes-ref]
     :or {notes-ref default/notes-ref}}]
   (let [cmd (if (nil? ref)
               (format "git notes list")
               (format "git notes list %s" ref))
         result (try (shell {:extra-env {"GIT_NOTES_REF" notes-ref}
                             :err :string
                             :out :string} cmd)
                     (catch Exception _ex
                       (let [causes (if ref #{:no-note} #{:no-notes})
                             msg (if ref
                                   (format "object %s has no note in GIT_NOTES_REF %s (exception)" ref notes-ref)
                                   (format "no notes in GIT_NOTES_REF %s (exception)" notes-ref))
                             exc (ex-info msg
                                          {:causes causes
                                           :object ref
                                           :notes-ref notes-ref})
                             err-data (ex-data exc)
                             err-msg (format "Cannot show note: %s" (ex-message exc))]
                         (debug (json/generate-string err-data))
                         {:err err-msg :out ""})))
         _err (str/trim-newline (:err result))
         out (str/trim-newline (:out result))]
     #_(debug (json/generate-string {:out out :err err}))
     (if (empty? out)
       (let [msg (if ref
                   (format "object %s has no note in GIT_NOTES_REF %s" ref notes-ref)
                   (format "no notes in GIT_NOTES_REF %s" notes-ref))]
         (debug msg)
         '())
       (let [s (if ref (format "%s %s" out ref) out)
             lines (str/split s #"\n")
             msg (if ref
                   (do (assert (= 1 (count lines))
                               (format "Found %s notes for object %s. In Git, each object (e.g., a commit, blob, tree) can have at most one note in a single note namespace." (count lines) ref))
                       (format "object %s has a note in %s" ref notes-ref))
                   (format "%s notes in %s" (count lines) notes-ref))]
         (debug msg)
         (map line->note-map lines))))))

(comment
  (def foo-notes-ref "refs/notes/foo")
  (def commit-not-annotated "e95918f917535a8606d9b741e7e5a46d65c35e84")
  (def commit-annotated-in-commits-and-foo "607b7134d11ac1d0f9f8dc1080f7c2757a086cf4")
  (def commit-annotated-just-in-foo "b2a1c31124d6aeba150c62285d6aed16a6067d18")

  (list)
  (list {:notes-ref foo-notes-ref})
  (list {:notes-ref "refs/notes/nonexistent-notes-ref"})

  (list {:ref commit-not-annotated})
  (list {:ref commit-annotated-just-in-foo})
  (list {:ref commit-annotated-just-in-foo :notes-ref foo-notes-ref})
  (list {:ref commit-annotated-in-commits-and-foo}))

(defn show
  "Show the notes for a given object (defaults to HEAD)."
  ([] (show {}))
  ([{:keys [ref
            notes-ref
            parse?]
     :or {ref (head)
          notes-ref default/notes-ref
          parse? false}}]
   (let [cmd (format "git notes show %s" ref)
         result (try (shell
                      {:extra-env {"GIT_NOTES_REF" notes-ref}
                       :err :string
                       :out :string} cmd)
                     (catch Exception _ex
                       (let [causes #{:no-note}
                             msg (format "Cannot show note: object %s has no note in GIT_NOTES_REF %s" ref notes-ref)
                             exc (ex-info msg {:causes causes
                                               :object ref
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

(comment
  (def foo-notes-ref "refs/notes/foo")
  (def commit-not-annotated "e95918f917535a8606d9b741e7e5a46d65c35e84")
  (def commit-annotated-in-commits-and-foo "607b7134d11ac1d0f9f8dc1080f7c2757a086cf4")
  (def commit-annotated-just-in-foo "b2a1c31124d6aeba150c62285d6aed16a6067d18")

  (show)
  (show {:ref commit-not-annotated})
  (show {:ref commit-annotated-just-in-foo :notes-ref foo-notes-ref})
  (show {:ref commit-annotated-in-commits-and-foo
         :notes-ref foo-notes-ref
         :parse? true})
  (show {:ref commit-annotated-in-commits-and-foo
         :notes-ref "/refs/notes/commits"
         :parse? true}))

(defn remove!
  ([] (remove! {}))
  ([{:keys [ref
            notes-ref
            ignore-missing?]
     :or {ref (head)
          notes-ref default/notes-ref
          ignore-missing? false}}]
   (let [cmd (format "git notes remove %s" ref)
         result (try (shell
                      {:extra-env {"GIT_NOTES_REF" notes-ref}
                       :err :string
                       :out :string} cmd)
                     (catch Exception _ex
                       (if ignore-missing?
                         {:err "" :out (format "Cannot remove: object %s has no note, but ignore-missing? was set to true" ref)}
                         (let [causes #{:no-note :no-ignore-missing}
                               msg (format "Cannot remove: object %s has no note" ref)
                               exc (ex-info msg {:causes causes
                                                 :object ref
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
  (def foo-notes-ref "refs/notes/foo")
  (def commit-not-annotated "e95918f917535a8606d9b741e7e5a46d65c35e84")
  (def commit-annotated-in-commits-and-foo "607b7134d11ac1d0f9f8dc1080f7c2757a086cf4")
  (def commit-annotated-just-in-foo "b2a1c31124d6aeba150c62285d6aed16a6067d18")

  (remove! {:ref commit-not-annotated})
  (remove! {:ref commit-not-annotated :ignore-missing? true})
  (remove! {:ref commit-annotated-in-commits-and-foo
            :notes-ref default/notes-ref}))

(defn add!
  [{:keys [force?
           ref
           message
           notes-ref]
    :or {force? false
         notes-ref default/notes-ref}}]
  (let [cmd (if force?
              (format "git notes add --force --message '%s' %s" message ref)
              (format "git notes add --message '%s' %s" message ref))
        result (try (debug cmd)
                    (shell {:extra-env {"GIT_NOTES_REF" notes-ref}
                            :err :string
                            :out :string} cmd)
                    (catch Exception _ex
                      (let [causes #{:note-exists :no-force}
                            ;; msg (.getMessage ^Throwable ex)
                            msg (format "Cannot add note: object %s already has a note in GIT_NOTES_REF %s. If you want to overwrite an existing note, set force? to true" ref notes-ref)
                            exc (ex-info msg {:causes causes
                                              :object ref
                                              :notes-ref notes-ref
                                              :force? force?})
                            err-data (ex-data exc)]
                        (warn (json/generate-string err-data))
                        {:err msg :out ""})))
        err (str/trim-newline (:err result))
        _out (str/trim-newline (:out result))]
    #_(debug (json/generate-string {:out out :err err}))
    (if (empty? err)
      {:message (format "Added note to object %s in GIT_NOTES_REF %s" ref notes-ref)}
      {:error err})))

(comment
  (def foo-notes-ref "refs/notes/foo")
  (def commit-not-annotated "e95918f917535a8606d9b741e7e5a46d65c35e84")
  (def commit-annotated-in-commits-and-foo "607b7134d11ac1d0f9f8dc1080f7c2757a086cf4")
  (def commit-annotated-just-in-foo "b2a1c31124d6aeba150c62285d6aed16a6067d18")

  (add! {:ref commit-annotated-in-commits-and-foo
         :message "Hello World"})

  (add! {:ref commit-not-annotated
         :message "Hello World"}))

(comment
  (-> (shell {:out :string} "git notes list") :out str/split-lines first)

  ;; (with-sh-env {"GIT_NOTES_REF" "refs/notes/foo"}
  ;;   (let [msg note-as-string]
  ;;     (sh "git" "-c" "user.name='Note Adder'" "-c" "user.email=giacomo@giacomodebidda.com" "notes" "add" "--force" "--message" msg commit-with-note)))
  )



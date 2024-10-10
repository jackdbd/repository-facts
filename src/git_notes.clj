(ns git-notes
  (:require
   [babashka.process :refer [shell]]
   [cheshire.core :as json]
   [clojure.core :refer [format]]
   [clojure.string :as str]
   [clojure.java.shell :refer [sh with-sh-env]]
   [default]
   [taoensso.timbre :refer [debug error info warn]]))

;; TODO: git notes append
;; TODO: git notes copy
;; TODO: git notes edit
;; TODO: git notes remove

(defn head []
  (let [{:keys [exit out]} (sh "git" "rev-parse" "HEAD")]
    (if (= 0 exit)
      (str/trim out)
      nil)))

(defn show-note
  ([] (show-note {}))
  ([{:keys [git-ref
            notes-ref
            parse?]
     :or {git-ref (head)
          notes-ref default/notes-ref
          parse? false}}]
   (let [result (try (shell
                      {:extra-env {"GIT_NOTES_REF" notes-ref}
                       :out :string} (format "git notes show %s" git-ref))
                     (catch Exception _ex
                       (debug "no note for REF" git-ref)
                       {:out ""}))
         s (str/trim-newline (:out result))]
     (if (empty? s)
       nil
       (if parse?
         (json/parse-string s true)
         s)))))

(defn line->note-map
  "A line is a string that has the following format:
   <note object SHA> <annotated object SHA>"
  [s]
  (let [splits (str/split s #" ")]
    {:note (first splits)
     :annotated (second splits)}))

(defn list-notes
  ([]
   (list-notes {}))
  ([{:keys [git-ref
            notes-ref]
     :or {git-ref nil
          notes-ref default/notes-ref}}]
   (let [cmd (if (nil? git-ref)
               (format "git notes list")
               (format "git notes list %s" git-ref))
         result (try (shell {:extra-env {"GIT_NOTES_REF" notes-ref}
                             :err :string
                             :out :string} cmd)
                     (catch Exception ex
                       (let [err (str/trim-newline (:err (ex-data ex)))]
                         (if git-ref
                           (warn "no notes for REF" git-ref)
                           (warn "no notes" err)))
                       {:out ""}))
         s (str/trim-newline (:out result))]
     (if (empty? s)
       '()
       (let [s (if git-ref (format "%s %s" s git-ref) s)
             lines (str/split s #"\n")]
         (map line->note-map lines))))))

(defn add-note!
  [{:keys [force?
           git-ref
           message
           notes-ref]
    :or {force? false
         notes-ref default/notes-ref}}]
  (let [cmd (if force?
              (format "git notes add --force --message %s %s" message git-ref)
              (format "git notes add --message %s %s" message git-ref))
        result (try (shell {:extra-env {"GIT_NOTES_REF" notes-ref}
                            :err :string
                            :out :string} cmd)
                    (catch Exception ex
                      (let [err (str/trim-newline (:err (ex-data ex)))]
                        (error err)
                        nil)))]
    (if (nil? result)
      nil
      (if-let [stderr (str/trim-newline (:err result))]
        (warn stderr)
        (info "note written to REF" git-ref)))))

(comment
  (def commit-with-note "607b7134d11ac1d0f9f8dc1080f7c2757a086cf4")
  (def commit-without-note "e95918f917535a8606d9b741e7e5a46d65c35e84")

  (add-note! {:git-ref commit-with-note :notes-ref "refs/notes/foo"
              :message note-as-string})
  (add-note! {:git-ref commit-with-note :notes-ref "refs/notes/foo"
              :force? true :message note-as-string})

  (head)

  (list-notes)
  (list-notes {:notes-ref "refs/notes/foo"})
  (list-notes {:notes-ref "refs/notes/bar"})
  (list-notes {:git-ref commit-with-note})
  (list-notes {:git-ref commit-with-note :notes-ref "refs/notes/foo"})
  (list-notes {:git-ref commit-without-note})

  (show-note)
  (show-note {:git-ref commit-with-note})
  (show-note {:git-ref commit-with-note :notes-ref "refs/notes/foo"})

  (show-note {:git-ref commit-with-note
              :notes-ref "refs/notes/foo"
              :parse? true})

  (with-sh-env {"GIT_NOTES_REF" default/notes-ref}
    (sh "git" "notes" "show" commit-with-note))

  (with-sh-env {"GIT_NOTES_REF" "refs/notes/foo"}
    (sh "git" "notes" "show" commit-with-note))

  (show-note {:git-ref commit-without-note})

  (shell {:extra-env {"FOO" "BAR"}} "git notes list")
  (-> (shell {:out :string} "git notes list") :out str/split-lines first)

  (def cmd (format "git notes list %s" commit-with-note))
  (def cmd (format "git notes list %s" commit-without-note))

  (def note-as-map {:first_run_at "some-time"
                    :last_run_at "some-other-time"
                    :environments [{"FOO" 123 "BAR" 456 "COLOR" "red" :run_on "GitHub Actions"}
                                   {"FOO" 789 "BAZ" 999 "COLOR" "blue" :run_on "GitHub Actions"}]
                    :diff {:added ["BAZ"]
                           :removed ["BAR"]
                           :modified ["COLOR"]}})

  (def note-as-string (json/generate-string note-as-map))
  (json/parse-string note-as-string true)

  (add-note! {:git-ref commit-with-note :notes-ref "refs/notes/foo"
              :message note-as-string})

  (with-sh-env {"GIT_NOTES_REF" "refs/notes/foo"}
    (let [msg note-as-string]
      (sh "git" "-c" "user.name='Note Adder'" "-c" "user.email=giacomo@giacomodebidda.com" "notes" "add" "--force" "--message" msg commit-with-note))))



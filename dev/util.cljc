(ns util
  (:require
   [babashka.process :refer [shell]]
   [clojure.string :as str]
   [default]
   [git :as git :refer [head]]
   [notes]))

(defn hostname []
  (-> (shell {:out :string} "hostname") :out str/trim-newline))

(defn env-reducer [acc cv] (assoc acc (keyword cv) (or (System/getenv cv) "")))

(defn env []
  ;; TIP: on Linux you can use `printenv` to see your environment variables.
  ;; These could be interesting: BROWSER, CLASSPATH, JAVA_HOME, PATH, PWD, XDG_CONFIG_DIRS
  (reduce env-reducer {}
          ["CC" "CLASSPATH" "CXX" "JAVA_HOME" "USER"]))

(defn tap-md-note>
  [{:keys [notes-ref object]
    :or {notes-ref default/notes-ref object (head)}}]
  (let [note (:notes/note (notes/show {:notes-ref notes-ref :object object}))]
    (tap> (with-meta
            [:portal.viewer/markdown note]
            {:portal.viewer/default :portal.viewer/hiccup}))))

(comment
  (env))
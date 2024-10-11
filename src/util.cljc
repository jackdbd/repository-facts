(ns util
  (:require
   [babashka.process :refer [shell]]
   [clojure.string :as str]))

(defn head
  "Return the commit hash of HEAD."
  []
  (let [cmd "git rev-parse HEAD"
        {:keys [exit out]} (shell {:err :string :out :string} cmd)]
    (if (= 0 exit)
      (str/trim out)
      nil)))

(comment
  (head))

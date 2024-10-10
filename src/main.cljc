(ns main
  (:require
   [cheshire.core :as json]))

#_(def obj {:foo {:entries [{:prop "alpha"}
                            {:prop "beta"}]}})
#_(fs/writeFileSync "foo.json" (js/JSON.stringify (clj->js obj)))

#_(def obj #js {:foo #js {:entries #js [#js {:prop "alpha"}
                                        #js {:prop "beta"}]}})
#_(fs/writeFileSync "foo.json" (js/JSON.stringify obj))

#_(println (fs/existsSync (fileURLToPath js/import.meta.url)))

(defn foo [{:keys [a b c]}]
  (+ a b c))

(println (foo {:a 1 :b 2 :c 3}))

(comment
  (+ 1 2))
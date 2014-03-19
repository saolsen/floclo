(ns floclo.main
  (:require [floclo.core :as f]
            [floclo.plugins.clojure :as clj]))

(def plugins {:main
              {:clj clj/eval-clj}})

(defn -main [org room]
  (f/start org room plugins))

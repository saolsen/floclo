(ns floclo.main
  (:require [floclo.core :as f]
            [floclo.plugins.clojure :as clj]
            [floclo.plugins.echo :as echo]))

(def plugins {:main
              {:clj clj/eval-clj
               :echo echo/echo}})

(defn -main [org room]
  (f/start org room plugins))

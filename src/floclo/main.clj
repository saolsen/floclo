(ns floclo.main
  (:require [clojure.edn :as edn]
            [floclo.core :as f]
            [floclo.plugins.clojure :as clj]
            [floclo.plugins.echo :as echo]))

(defn -main [org room plugin-file]
  (let [plugins (edn/read-string (slurp plugin-file))]
    (f/start org room plugins)))

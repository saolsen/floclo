(defproject floclo "0.1.0-SNAPSHOT"
  :description "Clojure evaluating flowdock bot."
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [org.clojure/data.json "0.2.3"]
                 [environ "0.4.0"]
                 [clj-http "0.7.8"]
                 [clojail "1.0.6"]]
  :profiles {:production {:dependencies [[ring/ring-jetty-adapter "1.2.1"]]
                          :main floclo.web}})
(ns floclo.web
  (:require [floclo.core :as core]
            [environ.core :refer [env]]
            [ring.adapter.jetty :refer [run-jetty]])
  (:gen-class))

;; Adds a little web server for no real reason except to let me deploy it
;; to heroku.

;; Requires a few more env vars. PORT, ORG and ROOM
(defn ok [req] {:status 200 :body "floclo"})

(defn -main []
  (let [web-server (run-jetty ok {:port (Integer/parseInt (env :port))
                                  :join? false})]
    (core/-main (env :org) (env :room))))

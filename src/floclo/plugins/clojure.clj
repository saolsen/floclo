(ns floclo.plugins.clojure
  (:require [clojail.core :refer [sandbox]]
            [clojail.testers :refer [secure-tester secure-tester-without-def]]
            [clojure.pprint :as p]
            [clojure.repl :as repl]
            [floclo.core :as f])
  (:import (java.io StringWriter)))

(def sb (sandbox secure-tester-without-def))

(defn sandbox-eval
  [clj-str ns-obj]
  (let [*read-eval* false
        writer (StringWriter.)
        result (sb (read-string clj-str) {#'*ns* ns-obj #'*out* writer})]
    {:out writer :result result}))

(defn init-ns
  [new-ns-name]
  (create-ns new-ns-name)
  (let [old-ns (ns-name *ns*)]
    (in-ns new-ns-name)
    (refer 'clojure.core)
    (in-ns old-ns)
    (find-ns new-ns-name)))

(defn eval-in-thread
  [thread-id clj-str]
  (let [thread-ns-name (symbol (str "fd" thread-id))]
    (when (nil? (find-ns thread-ns-name))
      (init-ns thread-ns-name))
    (sandbox-eval clj-str (find-ns thread-ns-name))))

(defn eval-clj
  [org room message]
  (println "IM HERE DONT IGNORE ME!")
  (let [content (get message "content")
        text (if (map? content) (get content "text") content)
        thread-id (f/get-thread-id message)]
    (try
      (println "THIS IS MY TEXT" text)
      (let [clj-str (f/strip-tag text)
            {:keys [out result]} (eval-in-thread thread-id clj-str)
            output (str out "\n    " (with-out-str (p/pprint result)))]
        (f/post-comment org room output thread-id))
      (catch Exception e
        (repl/pst e)
        (let [error (str "Error: " (.getName (class e)) ": " (.getMessage e))]
          (f/post-comment org room error thread-id))))))

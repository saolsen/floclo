(ns floclo.core
  (:require [clojail.core :refer [sandbox]]
            [clojail.testers :refer [secure-tester secure-tester-without-def]]
            [clojure.core.async :refer [chan thread >!! go <!! <!]]
            [clojure.data.json :as json]
            [clojure.pprint :as p]
            [clojure.string :as string]
            [clojure.repl :as repl]
            [environ.core :refer [env]]
            [clj-http.client :as req])
  (:import (java.io StringWriter)
           (clojure.lang ExceptionInfo))
  (:gen-class))

(defn streaming-endpoint [org room]
  (str "https://stream.flowdock.com/flows/" org "/" room))

(defn messages-endpoint [org room]
  (str "https://api.flowdock.com/flows/" org "/" room "/" "messages"))

(defn comments-endpoint [org room message-id]
  (str "https://api.flowdock.com/flows/" org "/" room "/messages/" message-id "/comments"))

;; Hardcoded to use basic auth define in environment for now.
(defn connect-to-flow-stream
  "Connects to the streaming endpoint."
  [org room]
  (let [req (req/get (streaming-endpoint org room)
                      {:basic-auth [(env :flowdock-username)
                                    (env :flowdock-password)]
                       :headers {"Accept" "application/json"}
                       :as :stream})]
    (:body req)))

(defn post-message
  "Posts a message."
  [org room message]
  (let [res (req/post (messages-endpoint org room)
                       {:basic-auth [(env :flowdock-username)
                                     (env :flowdock-password)]
                        :headers {"Content-Type" "application/json"}
                        :body (json/write-str {:event "message"
                                               :content message})})]
    (json/read-str (:body res))))

(defn post-comment
  "Posts a comment (threaded message)."
  [org room message message-id]
  (let [res (req/post (comments-endpoint org room message-id)
                       {:basic-auth [(env :flowdock-username)
                                     (env :flowdock-password)]
                        :headers {"Content-Type" "application/json"}
                        :body (json/write-str {:event "comment"
                                               :content message})})]
    (json/read-str (:body res))))

(defn to-message [char-seq]
  (->> char-seq
      (map char)
      (apply str)
      json/read-str))

(defn consume-stream
  "Returns a channel of messages from flowdock."
  [org room]
  (let [messages (chan)]
    (thread
     (while true
       (println "Connecting to flowdock " org ":" room)
        (let [stream (connect-to-flow-stream org room)
              r (clojure.java.io/reader stream)]
          (println "Connected")
          (loop [{:keys [s c]} {:s [] :c (.read r)}]
            (condp = c
              (int \return) (do (>!! messages (to-message s))
                                (recur {:s [] :c (.read r)}))
              -1 (println "Lost Connection")
              (recur {:s (conj s c)
                      :c (.read r)}))))))
    messages))

(defn get-thread-id
  [m]
  (let [influx (filterv #(.startsWith % "influx") (get m "tags"))]
    (if (seq influx)
      (subs (first influx) (inc (.indexOf (first influx) ":")))
      (get m "id"))))

(let [sb (sandbox secure-tester-without-def)]
  (defn sandbox-eval
    [clj-str ns-obj]
    (let [*read-eval* false
          writer (StringWriter.)
          result (sb (read-string clj-str) {#'*ns* ns-obj #'*out* writer})]
      {:out writer :result result})))

(defn init-ns
  [new-ns-name]
  (create-ns new-ns-name)
  (let [old-ns (ns-name *ns*)]
    (in-ns new-ns-name)
    (clojure.core/refer 'clojure.core)
    (in-ns old-ns)
    (find-ns new-ns-name)))

(defn eval-in-thread
  [thread-id clj-str]
  (let [thread-ns-name (symbol (str "fd" thread-id))]
    (when (nil? (find-ns thread-ns-name))
      (init-ns thread-ns-name))
    (sandbox-eval clj-str (find-ns thread-ns-name))))

(defn eval-clojure [org room c]
  (while true
    (let [m (<!! c)]
      (when (= (get m "app") "chat")
        (p/pprint m)
        (let [tags (get m "tags")
              content (get m "content")
              text (if (map? content) (get content "text") content)]
          (when (contains? (set tags) (or (env :flowdock-tag) "clj"))
            (try
              (let [clj-str (string/replace text (str "#" (or (env :flowdock-tag) "clj")) "")
                    {:keys [out result]} (eval-in-thread (get-thread-id m) clj-str)
                    output (str out  "\n    " (with-out-str (p/pprint result)))
                    ]
                (post-comment org room output (get-thread-id m)))
              (catch Exception e
                (repl/pst e)
                (let [message (str "Error: "
                                   (.getName (.getClass e))
                                   ": "
                                   (.getMessage e))
                      id (get-thread-id m)]
                  (post-comment org room message id))))))))))

(defn -main [org room]
  (eval-clojure org room (consume-stream org room)))

(ns floclo.core
  (:require [clojail.core :refer [sandbox]]
            [clojail.testers :refer [secure-tester]]
            [clojure.core.async :refer [chan thread >!! go <!! <!]]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [environ.core :refer [env]]
            [clj-http.client :as req])
  (:import (java.io StringWriter))
  (:gen-class))

(def sb (sandbox secure-tester))

(defn eval-clojure-safely
  [clj]
  (binding [*read-eval* false]
    (let [form (read-string clj)
          writer (StringWriter.)
          evald (sb form {#'*out* writer})]
      {:form form
       :out (str writer)
       :result evald})))

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

(defn eval-clojure [org room c]
   (while true
     (let [m (<!! c)]
       (when (= (get m "app") "chat")
         (println m)
         (let [tags (get m "tags")
               content (get m "content")
               text (if (map? content) (get content "text") content)]
           (when (contains? (set tags) (or (env :flowdock-tag) "clj"))
             (try
               (let [form (string/replace text (str "#" (or (env :flowdock-tag) "clj")) "")
                     {:keys [out result]} (eval-clojure-safely form)
                     output (str out  "\n    "
                                 (with-out-str
                                   (clojure.pprint/pprint result)))
                     ]
                 (post-comment org room output (get-thread-id m)))
               (catch Exception e
                 (post-message org
                               room
                               (str "Error evaluating expression." e))))))))))

(defn -main [org room]
  (eval-clojure org room (consume-stream org room)))

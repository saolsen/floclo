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
  (:import (clojure.lang ExceptionInfo))
  (:gen-class))

(defn streaming-endpoint [org room]
  (str "https://stream.flowdock.com/flows/" org "/" room))

(defn messages-endpoint [org room]
  (str "https://api.flowdock.com/flows/" org "/" room "/" "messages"))

(defn comments-endpoint [org room message-id]
  (str (messages-endpoint org room) "/" message-id "/comments"))

(def users-endpoint "https://api.flowdock.com/users")

(def auth [(env :flowdock-username)
           (env :flowdock-password)])

;; Hardcoded to use basic auth defined in environment for now.
(defn get-users
  "Pulls the users resource."
  []
  (let [res (req/get users-endpoint
                     {:basic-auth auth
                      :headers {"Accept" "application/json"}})]
    (json/read-str (:body res))))


(defn connect-to-flow-stream
  "Connects to the streaming endpoint."
  [org room]
  (let [req (req/get (streaming-endpoint org room)
                     {:basic-auth auth
                      :headers {"Accept" "application/json"}
                      :as :stream})]
    (:body req)))

(defn post-message
  "Posts a message."
  [org room message]
  (let [res (req/post (messages-endpoint org room)
                       {:basic-auth auth
                        :headers {"Content-Type" "application/json"}
                        :body (json/write-str {:event "message"
                                               :content message})})]
    (json/read-str (:body res))))

(defn post-comment
  "Posts a comment (threaded message)."
  [org room message message-id]
  (let [res (req/post (comments-endpoint org room message-id)
                       {:basic-auth auth
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
          (loop [s []
                 c (.read r)]
            (condp = c
              (int \return) (do (>!! messages (to-message s))
                                (recur [] (.read r)))
              -1 (println "Lost Connection")
              (recur (conj s c) (.read r)))))))
    messages))

(defn get-user-id
  "Gets the :flowdock-user id."
  []
  (let [users (get-users)
        mine (first (filter #(= (get % "email") (env :flowdock-username)) users))]
    (get mine "id")))

(defn get-thread-id
  [m]
  (let [influx (filterv #(.startsWith % "influx") (get m "tags"))]
    (if (seq influx)
      (subs (first influx) (inc (.indexOf (first influx) ":")))
      (get m "id"))))

(def tag (or (env :flowdock-tag) "clj"))

(defn strip-tag
  [s]
  (string/replace s (str "#" tag) ""))

(defn start [org room plugins-map]
  (let [c (consume-stream org room)
        current-user-id (str (get-user-id))]
    (while true
      (let [m (<!! c)]
        (when (= (get m "app") "chat")
          (p/pprint m)
          (let [tags (get m "tags")
                tag-map (get plugins-map (keyword room))
                plugins (remove nil? (map tag-map (map keyword tags)))
                user (get m "user")]
            (when-not (= user current-user-id)
              (doall (map #(% org room m) plugins)))))))))

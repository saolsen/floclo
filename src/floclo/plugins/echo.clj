(ns floclo.plugins.echo
  (:require [floclo.core :as f]))

(defn echo
  [org room message]
  (let [content (get message "content")
        text (if (map? content) (get content "text") content)
        thread-id (f/get-thread-id message)]
    (f/post-comment org room (f/strip-tags text) thread-id)))

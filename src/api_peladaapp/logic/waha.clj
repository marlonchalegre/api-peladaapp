(ns api-peladaapp.logic.waha
  (:require
   [api-peladaapp.config :as config]
   [clj-http.client :as http]
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]))

(defn send-message
  "Sends a text message via WAHA API.
   config: {:waha-api-url ... :waha-instance ... :waha-group-id ...}
   text: the message content
   mentions: (optional) list of jids to mention, or [\"all\"]"
  ([config text] (send-message config text nil))
  ([{:keys [waha-api-url waha-instance waha-group-id]} text mentions]
   (let [url (str waha-api-url "/api/sendText")
         api-key (config/get-key :waha-api-key)
         payload (cond-> {:session waha-instance
                          :chatId waha-group-id
                          :text text}
                   (seq mentions) (assoc :mentions mentions))
         body (json/write-str payload)]
     (try
       (log/info "Sending WAHA message to" waha-group-id "via" waha-instance (when (seq mentions) (str "mentions: " mentions)))
       (http/post url
                  {:body body
                   :content-type :json
                   :accept :json
                   :headers (when api-key {"X-Api-Key" api-key})})
       (catch Exception e
         (log/error e "Failed to send WAHA message")
         {:error (.getMessage e)})))))

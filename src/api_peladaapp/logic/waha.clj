(ns api-peladaapp.logic.waha
  (:require
   [api-peladaapp.config :as config]
   [clj-http.client :as http]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.tools.logging :as log]))

(defn normalize-phone
  "Normalizes a phone number for WhatsApp JID.
   For Brazilian numbers (starting with 55):
   - If DDD is 11-28, keep the 9th digit.
   - If DDD is > 28, remove the 9th digit (if present).
   Always returns digits only + @c.us"
  [phone]
  (let [digits (str/replace (or phone "") #"\D" "")]
    (if (str/starts-with? digits "55")
      (let [ddd (subs digits 2 4)
            number (subs digits 4)
            ddd-int (try (Integer/parseInt ddd) (catch Exception _ 0))]
        (if (and (> ddd-int 28) (= (count number) 9) (str/starts-with? number "9"))
          (str "55" ddd (subs number 1) "@c.us")
          (str digits "@c.us")))
      (if (seq digits)
        (str digits "@c.us")
        nil))))

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

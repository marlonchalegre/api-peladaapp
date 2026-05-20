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
         default-waha-url (System/getenv "WAHA_API_URL") ;; Assuming this is how it's configured in prod
         api-key (config/get-key :waha-api-key)
         ;; SECURITY: Only send global API key if the URL matches the trusted internal/default URL.
         ;; In dev, we might allow localhost/backend.
         is-trusted? (or (str/blank? waha-api-url)
                         (= waha-api-url default-waha-url)
                         (str/starts-with? waha-api-url "http://backend:")
                         (str/starts-with? waha-api-url "http://waha:"))
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
                   :headers (when (and api-key is-trusted?) {"X-Api-Key" api-key})})
       (catch Exception e
         (log/error e "Failed to send WAHA message")
         {:error (.getMessage e)})))))

(defn send-poll
  "Sends a poll via WAHA API.
   config: {:waha-api-url ... :waha-instance ... :waha-group-id ...}
   question: the poll question
   options: list of strings
   multiple-answers?: boolean (default false)"
  ([config question options] (send-poll config question options false))
  ([{:keys [waha-api-url waha-instance waha-group-id]} question options multiple-answers?]
   (let [url (str waha-api-url "/api/sendPoll")
         default-waha-url (System/getenv "WAHA_API_URL")
         api-key (config/get-key :waha-api-key)
         is-trusted? (or (str/blank? waha-api-url)
                         (= waha-api-url default-waha-url)
                         (str/starts-with? waha-api-url "http://backend:")
                         (str/starts-with? waha-api-url "http://waha:"))
         payload {:session waha-instance
                  :chatId waha-group-id
                  :poll {:name question
                         :options options
                         :multipleAnswers (boolean multiple-answers?)}}
         body (json/write-str payload)]
     (try
       (log/info "Sending WAHA poll to" waha-group-id "via" waha-instance ":" question)
       (http/post url
                  {:body body
                   :content-type :json
                   :accept :json
                   :headers (when (and api-key is-trusted?) {"X-Api-Key" api-key})})
       (catch Exception e
         (log/error e "Failed to send WAHA poll")
         {:error (.getMessage e)})))))

(defn healthcheck
  "Checks if WAHA service is up by calling server version endpoint."
  []
  (let [base-url (or (System/getenv "WAHA_API_URL") "http://localhost:8080/waha")
        url (str base-url "/api/server/version")
        api-key (config/get-key :waha-api-key)]
    (try
      (let [response (http/get url
                               {:accept :json
                                :headers (when api-key {"X-Api-Key" api-key})})]
        (if (= 200 (:status response))
          {:status "UP" :details (json/read-str (:body response))}
          {:status "DOWN" :error (str "Unexpected status: " (:status response))}))
      (catch Exception e
        {:status "DOWN" :error (.getMessage e)}))))

(defn resume-session
  "Starts a WAHA session by name."
  [session-name]
  (let [base-url (or (System/getenv "WAHA_API_URL") "http://localhost:8080/waha")
        url (str base-url "/api/sessions/" session-name "/start")
        api-key (config/get-key :waha-api-key)]
    (try
      (let [response (http/post url
                                {:accept :json
                                 :headers (when api-key {"X-Api-Key" api-key})})]
        (if (or (= 200 (:status response)) (= 201 (:status response)))
          {:status "success" :message (str "Session " session-name " started/resumed")}
          {:status "error" :error (str "Unexpected status: " (:status response))}))
      (catch Exception e
        {:status "error" :error (.getMessage e)}))))

(defn start-session
  "Creates and starts a new WAHA session."
  [session-name]
  (let [base-url (or (System/getenv "WAHA_API_URL") "http://localhost:8080/waha")
        url (str base-url "/api/sessions")
        api-key (config/get-key :waha-api-key)
        payload {:name session-name
                 :engine "GOWS" ;; Using GOWS as per docker-compose.yml
                 :start true}
        body (json/write-str payload)]
    (try
      (let [response (http/post url
                                {:body body
                                 :content-type :json
                                 :accept :json
                                 :headers (when api-key {"X-Api-Key" api-key})})]
        (if (or (= 200 (:status response)) (= 201 (:status response)))
          {:status "success" :message (str "Session " session-name " created and started")}
          {:status "error" :error (str "Unexpected status: " (:status response))}))
      (catch Exception e
        {:status "error" :error (.getMessage e)}))))

(defn stop-session
  "Stops/Deletes a WAHA session."
  [session-name]
  (let [base-url (or (System/getenv "WAHA_API_URL") "http://localhost:8080/waha")
        url (str base-url "/api/sessions/" session-name)
        api-key (config/get-key :waha-api-key)]
    (try
      (let [response (http/delete url
                                  {:accept :json
                                   :headers (when api-key {"X-Api-Key" api-key})})]
        (if (contains? #{200 201 204} (:status response))
          {:status "success" :message (str "Session " session-name " stopped/deleted")}
          {:status "error" :error (str "Unexpected status: " (:status response))}))
      (catch Exception e
        {:status "error" :error (.getMessage e)}))))

(defn sleep [ms]
  (Thread/sleep ms))

(defn restart-session
  "Restarts a WAHA session (stop, wait 5s, start)."
  [session-name]
  (log/info "Restarting WAHA session:" session-name)
  (stop-session session-name)
  (log/info "Waiting 5 seconds for cleanup...")
  (sleep 5000)
  (start-session session-name))

(ns api-peladaapp.test-helpers
  "Test helpers for the api-peladaapp project."
  (:require
   [api-peladaapp.components :as components]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [com.stuartsierra.component :as component]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(def ^:dynamic *test-system* nil)

(defn- temp-db-file []
  (let [f (java.io.File/createTempFile "peladaapp-test-" ".db")]
    (.deleteOnExit f)
    (.getAbsolutePath f)))

(defn- get-migration-files []
  (let [dir (java.io.File. "resources/migrations")]
    (->> (.listFiles dir)
         (map #(.getPath %))
         (filter #(clojure.string/ends-with? % ".up.sql"))
         sort)))

(defn migrate! [db-file]
  (let [ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
        migration-files (get-migration-files)]
    (with-open [conn (jdbc/get-connection ds)]
      (doseq [sql-file migration-files
              :let [content (slurp sql-file)
                    statements (if (str/includes? content "--;;")
                                 (->> (str/split content #"\-\-;;")
                                      (map str/trim)
                                      (remove str/blank?))
                                 (->> (str/split content #";[\r\n]+")
                                      (map str/trim)
                                      (remove str/blank?)))]
              stmt statements]
        (jdbc/execute! conn [stmt])))))

(defn make-system []
  (let [db-file (temp-db-file)
        _ (migrate! db-file)
        system (dissoc (components/system {:db-spec {:dbname db-file} :skip-migrations true}) :server)]
    (assoc system :db-file db-file)))

(defn test-system-fixture [f]
  (let [system (component/start (make-system))]
    (binding [*test-system* system]
      (try
        (f)
        (finally
          (component/stop system))))))

(defn decode-body [resp]
  (let [b (:body resp)]
    (cond
      (map? b) b
      (string? b) (when-not (str/blank? b)
                    (try (json/read-str b :key-fn keyword)
                         (catch Exception _ nil)))
      (instance? java.io.InputStream b) (let [s (slurp b)]
                                          (when-not (str/blank? s)
                                            (try (json/read-str s :key-fn keyword)
                                                 (catch Exception _ nil))))
      :else nil)))

(defn auth-header [token]
  (fn [req] (mock/header req "Authorization" (str "Token " token))))

(defn register-and-login! [app {:keys [name email password username phone] :or {username (str "user" (rand-int 1000))}}]
  (app (-> (mock/request :post "/auth/register") (mock/json-body {:name name :email email :username username :password password :phone phone})))
  (let [login (app (-> (mock/request :post "/auth/login") (mock/json-body {:email (or email username) :password password})))
        token (:token (decode-body login))]
    token))

(defn user-id-by-email [ds email]
  (let [row (first (jdbc/execute! ds ["select id from Users where email = ?" email]))]
    (when row
      (or (:id row)
          (:Users/id row)
          (:users/id row)
          (get row "id")))))

(defn player-id-by-user-id [ds user-id org-id]
  (let [row (first (jdbc/execute! ds ["select id from OrganizationPlayers where user_id = ? and organization_id = ?" user-id org-id]))]
    (when row
      (or (:id row)
          (:OrganizationPlayers/id row)
          (get row "id")))))

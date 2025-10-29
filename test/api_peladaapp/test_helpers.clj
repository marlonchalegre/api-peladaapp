(ns api-peladaapp.test-helpers
  (:require
   [api-peladaapp.components :as components]
   [api-peladaapp.server :as server]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(defn- temp-db-file []
  (let [f (java.io.File/createTempFile "peladaapp-test-" ".db")] 
    (.deleteOnExit f)
    (.getAbsolutePath f)))

(def migration-files
  ["resources/migrations/20251028150000-init_all.up.sql"
   "resources/migrations/20251028160000-match_events.up.sql"
   "resources/migrations/20251029183000-match_lineups.up.sql"])

(defn migrate! [db-file]
  (let [ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    (with-open [conn (jdbc/get-connection ds)]
      (doseq [sql-file migration-files
              :let [content (slurp sql-file)
                    statements (->> (str/split content #";[\r\n]+")
                                    (map str/trim)
                                    (remove str/blank?))]
              stmt statements]
        (jdbc/execute! conn [stmt])))))

(defn make-app! []
  (let [db-file (temp-db-file)
        _ (migrate! db-file)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
        db-fn (fn [] ds)
        app (components/wrap-assoc server/app :database db-fn)]
    {:app app :db-file db-file}))

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

(defn register-and-login! [app {:keys [name email password]}]
  (app (-> (mock/request :post "/auth/register") (mock/json-body {:name name :email email :password password})))
  (let [login (app (-> (mock/request :post "/auth/login") (mock/json-body {:email email :password password})))
        token (:token (decode-body login))]
    token))

(defn user-id-by-email [ds email]
  (let [row (first (jdbc/execute! ds ["select id from Users where email = ?" email]))]
    (when row
      (or (:id row)
          (:Users/id row)
          (:users/id row)
          (get row "id")))))

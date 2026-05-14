(ns api-peladaapp.test-helpers
  (:require
   [api-peladaapp.components :as components]
   [api-peladaapp.helpers.sql :as hsql]
   [cheshire.core :as json]
   [clojure.string :as str]
   [com.stuartsierra.component :as component]
   [honey.sql.helpers :as h]
   [migratus.core :as migratus]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [ring.mock.request :as mock])
  (:import
   (java.net URI)))

(def ^:dynamic *test-system* nil)
(def ^:private TEST_SCHEMA "test")

(defn decode-body [resp]
  (try
    (json/parse-string (:body resp) true)
    (catch Exception _
      (:body resp))))

(defn auth-cookie
  ([token] (fn [req] (mock/cookie req "authToken" token)))
  ([req token] (mock/cookie req "authToken" token)))

(declare user-id-by-email player-id-by-user-id)

(defn register-and-login! [app {:keys [name email password username phone] :or {username (str "user-" (System/currentTimeMillis) "-" (rand-int 10000))}}]
  (let [email (or email (str "test-" (rand-int 100000) "@test.com"))
        password (or password "pass123")
        name (or name "Test User")]
    (app (-> (mock/request :post "/auth/register")
             (mock/json-body {:name name :email email :username username :password password :phone phone})))
    (let [login-req (-> (mock/request :post "/auth/login")
                        (mock/json-body {:email email :password password}))
          login-resp (app login-req)
          login-body (decode-body login-resp)
          token (:token login-body)
          ds (-> *test-system* :database :database)
          ds (if (fn? ds) (ds) ds)]
      ;; Ensure the user record is visible in the DB before proceeding to inserts.
      (loop [i 0]
        (when (< i 10)
          (when-not (user-id-by-email ds email)
            (Thread/sleep 100)
            (recur (inc i)))))
      token)))

(defn- ensure-uuid [x]
  (if (string? x) (parse-uuid x) x))

(defn user-id-by-email [ds email]
  (let [query (-> (h/select :*)
                  (h/from :Users)
                  (h/where [:= [:lower :email] [:lower email]]))
        sql-str (hsql/format query)
        row (jdbc/execute-one! ds sql-str {:builder-fn rs/as-unqualified-lower-maps})]
    (:id row)))

(defn player-id-by-user-id [ds user-id org-id]
  (let [user-id (ensure-uuid user-id)
        org-id (ensure-uuid org-id)
        query (-> (h/select :id)
                  (h/from :OrganizationPlayers)
                  (h/where [:= :user_id user-id] [:= :organization_id org-id]))
        row (jdbc/execute-one! ds (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps})]
    (:id row)))

(defonce ^:private postgres-ds (atom nil))

(defn- parse-database-url [url]
  (let [uri (URI. url)
        user-info (.getUserInfo uri)
        [user password] (if user-info (str/split user-info #":") [nil nil])
        host (.getHost uri)
        port (.getPort uri)
        path (.getPath uri)
        dbname (if (and path (> (count path) 1)) (subs path 1) nil)
        base-url (str "jdbc:postgresql://" host ":" (if (= port -1) 5432 port) "/" dbname
                      (when user (str "?user=" user))
                      (when password (str "&password=" password)))]
    ;; Append test schema
    (str base-url (if (str/includes? base-url "?") "&" "?") "currentSchema=" TEST_SCHEMA)))

(defn- get-or-create-postgres-ds [jdbc-url]
  (if-let [ds @postgres-ds]
    ds
    (let [new-ds (jdbc/get-datasource jdbc-url)]
      (reset! postgres-ds new-ds)
      new-ds)))

(defn get-test-datasource
  "Return a datasource for tests. DATABASE_URL must be set."
  []
  (if-let [db-url (System/getenv "DATABASE_URL")]
    (let [jdbc-url (parse-database-url db-url)]
      (get-or-create-postgres-ds jdbc-url))
    (throw (ex-info "No database configured for tests (DATABASE_URL is missing)" {}))))

(defn recreate-test-schema [ds]
  (jdbc/execute! ds [(str "DROP SCHEMA IF EXISTS " TEST_SCHEMA " CASCADE")])
  (jdbc/execute! ds [(str "CREATE SCHEMA " TEST_SCHEMA)])
  (jdbc/execute! ds [(str "GRANT ALL ON SCHEMA " TEST_SCHEMA " TO public")]))

(defn- seed-positions [ds]
  (let [positions [{:value "Goalkeeper"}
                   {:value "Defender"}
                   {:value "Midfielder"}
                   {:value "Striker"}]]
    (doseq [pos positions]
      (try
        (jdbc/execute! ds (hsql/format (-> (h/insert-into :Positions) (h/values [pos]))))
        (catch Exception _)))))

(defonce migrations-run? (atom false))

(defn test-system-fixture [f]
  (let [ds (get-test-datasource)]
    ;; We recreate the schema and run migrations for EVERY test to ensure total isolation.
    ;; If this becomes too slow, we can optimize to only truncate, but recreating the schema
    ;; avoids the need to maintain a list of tables.
    (recreate-test-schema ds)
    (let [config {:store :database
                  :migration-dir "migrations"
                  :db {:datasource ds}
                  :schema TEST_SCHEMA}]
      (migratus/migrate config))
    (seed-positions ds)
    (binding [*test-system* (let [sys (components/system {:db-spec {:datasource ds} :skip-migrations true})]
                              (-> sys
                                  (dissoc :server)
                                  (assoc-in [:database :database] ds)
                                  component/start))]
      (try
        (f)
        (finally
          (component/stop *test-system*))))))

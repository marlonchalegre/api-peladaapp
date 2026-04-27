(ns api-peladaapp.attendance-test
  (:require
   [api-peladaapp.db.attendance :as db.attendance]
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.test-helpers :as th]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is use-fixtures]]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(defn- decode-body [resp]
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

(deftest attendance-flow-test
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    ;; Register and login user
    (app (-> (mock/request :post "/auth/register") (mock/json-body {:name "Player 1" :email "p1@ex.com" :password "p"})))
    (let [login (app (-> (mock/request :post "/auth/login") (mock/json-body {:email "p1@ex.com" :password "p"})))
          token (:token (decode-body login))
          auth (th/auth-cookie token)
          user-id (th/user-id-by-email ds "p1@ex.com")

          ;; Create organization (user becomes admin automatically)
          org-resp (app (-> (mock/request :post "/api/organizations")
                            (mock/json-body {:name "Attendance Club"})
                            auth))
          org-id (:id (decode-body org-resp))]
      (is (= 201 (:status org-resp)))

      ;; Get admin player id and make them mensalista so they skip waitlist
      (let [players (decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/players")) auth)))
            p1-id (:id (first (filter #(= (:user_id %) user-id) players)))]
        (app (-> (mock/request :put (str "/api/players/" p1-id))
                 (mock/json-body {:member_type "mensalista"})
                 auth)))

        ;; Create pelada
      (let [pelada-resp (app (-> (mock/request :post "/api/peladas")
                                 (mock/json-body {:organization_id org-id})
                                 auth))
            pelada-id (:id (decode-body pelada-resp))]
        (is (= 201 (:status pelada-resp)))

          ;; Update attendance (this was causing 500 error)
        (let [att-resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/attendance"))
                                (mock/json-body {:status "confirmed"})
                                auth))
              att-body (decode-body att-resp)]
          (is (= 200 (:status att-resp)))
          (is (= 1 (:result att-body)))
          (is (not (instance? java.lang.Integer (:body att-resp))))

            ;; Verify database state
          (let [check-resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/full-details"))
                                    auth))
                pelada-data (decode-body check-resp)
                attendance (:attendance pelada-data)
                available (:available_players pelada-data)]
              ;; Security check: password must not be returned
            (is (not (contains? (get-in available [0 :user]) :password)))
            (is (not (contains? (get-in attendance [0 :player :user]) :password)))

              ;; Check attendance list
            (is (some (fn [a] (and (= (:status a) "confirmed")
                                   (= (get-in a [:player :user_id]) user-id)))
                      attendance))
              ;; Check available_players list (which is what the frontend uses)
            (is (some (fn [p] (and (= (:attendance_status p) "confirmed")
                                   (= (:user_id p) user-id)))
                      available))))

          ;; Close attendance
        (let [close-resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close-attendance"))
                                  auth))]
          (is (= 200 (:status close-resp))))))))

(deftest batch-attendance-test
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    ;; 1. Register admin and 2 more users
    (app (-> (mock/request :post "/auth/register") (mock/json-body {:name "Admin" :email "admin@ex.com" :password "p"})))
    (app (-> (mock/request :post "/auth/register") (mock/json-body {:name "User 2" :email "u2@ex.com" :password "p"})))
    (app (-> (mock/request :post "/auth/register") (mock/json-body {:name "User 3" :email "u3@ex.com" :password "p"})))

    (let [login (app (-> (mock/request :post "/auth/login") (mock/json-body {:email "admin@ex.com" :password "p"})))
          token (:token (decode-body login))
          auth (th/auth-cookie token)
          admin-id (th/user-id-by-email ds "admin@ex.com")
          u2-id (th/user-id-by-email ds "u2@ex.com")
          u3-id (th/user-id-by-email ds "u3@ex.com")

          ;; 2. Create organization (automatically creates player for admin with grade 5.0)
          org-resp (app (-> (mock/request :post "/api/organizations")
                            (mock/json-body {:name "Batch Club"})
                            auth))
          org-id (:id (decode-body org-resp))

          ;; 3. Create players for other users in this org
          p2-resp (app (-> (mock/request :post "/api/players") (mock/json-body {:organization_id org-id :user_id u2-id}) auth))
          p3-resp (app (-> (mock/request :post "/api/players") (mock/json-body {:organization_id org-id :user_id u3-id}) auth))

          p2-id (:id (decode-body p2-resp))
          p3-id (:id (decode-body p3-resp))

          ;; Find admin's player id (id 1 usually, but let's be safe)
          all-players (decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/players")) auth)))
          p1-id (:id (first (filter #(= (:user_id %) admin-id) all-players)))

          ;; 4. Create pelada
          pelada-resp (app (-> (mock/request :post "/api/peladas")
                               (mock/json-body {:organization_id org-id})
                               auth))
          pelada-id (:id (decode-body pelada-resp))

          ;; 5. Batch update attendance for p2 and p3
          batch-resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/attendance/batch"))
                              (mock/json-body {:player_ids [p2-id p3-id] :status "confirmed"})
                              auth))]

      (is (= 200 (:status batch-resp)))
      (is (= 2 (:result (decode-body batch-resp))))

      ;; 6. Verify they are confirmed
      (let [details-resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/full-details")) auth))
            available (:available_players (decode-body details-resp))]
        (is (some (fn [p] (and (= (:id p) p2-id) (= (:attendance_status p) "confirmed"))) available))
        (is (some (fn [p] (and (= (:id p) p3-id) (= (:attendance_status p) "confirmed"))) available))
        (is (some (fn [p] (and (= (:id p) p1-id) (= (:attendance_status p) "pending"))) available))))))

(deftest convidado-waitlist-test
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    ;; 1. Register admin and 1 convidado user
    (app (-> (mock/request :post "/auth/register") (mock/json-body {:name "Admin" :email "admin@ex.com" :password "p"})))
    (app (-> (mock/request :post "/auth/register") (mock/json-body {:name "Convidado" :email "convidado@ex.com" :password "p"})))

    (let [admin-login (app (-> (mock/request :post "/auth/login") (mock/json-body {:email "admin@ex.com" :password "p"})))
          admin-token (:token (decode-body admin-login))
          admin-auth (th/auth-cookie admin-token)

          convidado-login (app (-> (mock/request :post "/auth/login") (mock/json-body {:email "convidado@ex.com" :password "p"})))
          convidado-token (:token (decode-body convidado-login))
          convidado-auth (th/auth-cookie convidado-token)
          convidado-id (th/user-id-by-email ds "convidado@ex.com")

          ;; 2. Create organization
          org-resp (app (-> (mock/request :post "/api/organizations")
                            (mock/json-body {:name "Waitlist Club"})
                            admin-auth))
          org-id (:id (decode-body org-resp))

          ;; 3. Create player for convidado (defaults to convidado)
          p-resp (app (-> (mock/request :post "/api/players")
                          (mock/json-body {:organization_id org-id :user_id convidado-id})
                          admin-auth))
          p-id (:id (decode-body p-resp))

          ;; 4. Create pelada
          pelada-resp (app (-> (mock/request :post "/api/peladas")
                               (mock/json-body {:organization_id org-id})
                               admin-auth))
          pelada-id (:id (decode-body pelada-resp))]

      ;; 5. Verify member_type is convidado
      (let [players-resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/players")) admin-auth))
            players (decode-body players-resp)]
        (is (some (fn [p] (and (= (:id p) p-id) (= (:member_type p) "convidado"))) players)))

      ;; 6. Convidado tries to confirm attendance
      (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/attendance"))
               (mock/json-body {:status "confirmed"})
               convidado-auth))

      ;; 7. Verify convidado is in waitlist
      (let [details-resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/full-details")) admin-auth))
            available (:available_players (decode-body details-resp))]
        (is (some (fn [p] (and (= (:id p) p-id) (= (:attendance_status p) "waitlist"))) available)))

      ;; 8. Admin moves convidado to confirmed
      (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/attendance"))
               (mock/json-body {:player_id p-id :status "confirmed"})
               admin-auth))

      ;; 9. Verify convidado is now confirmed
      (let [details-resp2 (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/full-details")) admin-auth))
            available2 (:available_players (decode-body details-resp2))]
        (is (some (fn [p] (and (= (:id p) p-id) (= (:attendance_status p) "confirmed"))) available2))))))

(deftest list-pending-attendance-test
  (let [db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]

    ;; 1. Setup organization
    (let [user-id (db.user/insert-user {:name "Admin" :email "admin-notification@test.com" :password "pass"} ds)
          org-id (db.organization/insert-organization {:name "Notification Org" :owner-id user-id} ds)

          ;; 2. Setup players: 1 mensalista, 1 diarista
          p1-id (jdbc/execute-one! ds ["INSERT INTO OrganizationPlayers (organization_id, user_id, member_type) VALUES (?, ?, ?) RETURNING id"
                                       org-id user-id "mensalista"]
                                   {:return-keys true :builder-fn next.jdbc.result-set/as-unqualified-lower-maps})
          p1-id (:id p1-id)

          user2-id (db.user/insert-user {:name "Diarista" :email "diarista-notification@test.com" :password "pass"} ds)
          p2-id (jdbc/execute-one! ds ["INSERT INTO OrganizationPlayers (organization_id, user_id, member_type) VALUES (?, ?, ?) RETURNING id"
                                       org-id user2-id "diarista"]
                                   {:return-keys true :builder-fn next.jdbc.result-set/as-unqualified-lower-maps})
          p2-id (:id p2-id)

          ;; 3. Create pelada
          pelada-id (db.pelada/insert-pelada {:organization-id org-id :status "attendance"} ds)]

      ;; Initial state: only mensalista should be listed
      (let [pending (db.attendance/list-pending-mensalistas-by-pelada pelada-id ds)]
        (is (= 1 (count pending)))
        (is (some #(= (:player-name %) "Admin") pending))
        (is (not (some #(= (:player-name %) "Diarista") pending))))

      ;; 4. If mensalista confirms, they should be removed from pending
      (db.attendance/upsert-attendance pelada-id p1-id "confirmed" ds)
      (let [pending (db.attendance/list-pending-mensalistas-by-pelada pelada-id ds)]
        (is (= 0 (count pending))))

      ;; 5. If mensalista declines, they should also be removed from pending
      (db.attendance/upsert-attendance pelada-id p1-id "declined" ds)
      (let [pending (db.attendance/list-pending-mensalistas-by-pelada pelada-id ds)]
        (is (= 0 (count pending))))

      ;; 6. If mensalista is 'pending', they should still be listed
      (db.attendance/upsert-attendance pelada-id p1-id "pending" ds)
      (let [pending (db.attendance/list-pending-mensalistas-by-pelada pelada-id ds)]
        (is (= 1 (count pending)))
        (is (some #(= (:player-name %) "Admin") pending)))

      ;; 7. If all mensalistas have responded (confirmed/declined/waitlist) but diaristas haven't
      ;; p1 is currently 'pending', let's make them 'confirmed'
      (db.attendance/upsert-attendance pelada-id p1-id "confirmed" ds)
      ;; p2 is a 'diarista' and has NOT responded yet (no record in PeladaAttendance)
      (let [pending (db.attendance/list-pending-mensalistas-by-pelada pelada-id ds)]
        (is (= 0 (count pending)))))))




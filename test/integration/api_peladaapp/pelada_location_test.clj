(ns integration.api-peladaapp.pelada-location-test
  (:require
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.test-helpers :as th]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(defn- exec! [ds query]
  (jdbc/execute! ds (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps}))

(defn- add-member!
  ([ds org-id user-id]
   (add-member! ds org-id user-id "diarista"))
  ([ds org-id user-id member-type]
   (or (th/player-id-by-user-id ds user-id org-id)
       (do
         (exec! ds (-> (h/insert-into :OrganizationPlayers)
                       (h/values [{:organization_id org-id
                                   :user_id user-id
                                   :grade 5.0
                                   :member_type [:cast member-type :member_type]}])))
         (th/player-id-by-user-id ds user-id org-id)))))

(deftest pelada-location-round-trip-test
  (let [app (-> th/*test-system* :app :app-handler)
        token (th/register-and-login! app {:name "Location Admin" :email "loc_admin@test.com" :password "pass123"})
        org-id (:id (th/decode-body (app (-> (mock/request :post "/api/organizations")
                                             (mock/json-body {:name "Org Location"})
                                             (th/auth-cookie token)))))
        create-resp (app (-> (mock/request :post "/api/peladas")
                             (mock/json-body {:organization_id org-id
                                              :name "Pelada com Local"
                                              :scheduled_at "2026-09-24T19:00:00Z"
                                              :location "Arena Vila Nova · Q2"
                                              :max_players 20})
                             (th/auth-cookie token)))
        create-body (th/decode-body create-resp)
        pelada-id (:id create-body)]

    (testing "creating a pelada persists and returns its location"
      (is (= 201 (:status create-resp)))
      (is (= "Arena Vila Nova · Q2" (:location create-body))))

    (testing "full-details exposes the stored location"
      (let [details (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/full-details"))
                                             (th/auth-cookie token))))]
        (is (= "Arena Vila Nova · Q2" (get-in details [:pelada :location])))))

    (testing "updating the pelada changes its location"
      (let [update-resp (app (-> (mock/request :put (str "/api/peladas/" pelada-id))
                                 (mock/json-body {:location "Quadra do Parque · Coberta"})
                                 (th/auth-cookie token)))
            update-body (th/decode-body update-resp)
            details (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/full-details"))
                                             (th/auth-cookie token))))]
        (is (= 200 (:status update-resp)))
        (is (= "Quadra do Parque · Coberta" (:location update-body)))
        (is (= "Quadra do Parque · Coberta" (get-in details [:pelada :location])))))))

(deftest pelada-confirmed-count-and-preview-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        admin-token (th/register-and-login! app {:name "Preview Admin" :email "prev_admin@test.com" :password "pass123"})
        admin-user-id (th/user-id-by-email ds "prev_admin@test.com")
        org-id (parse-uuid (:id (th/decode-body (app (-> (mock/request :post "/api/organizations")
                                                         (mock/json-body {:name "Org Preview"})
                                                         (th/auth-cookie admin-token))))))
        pelada-id (:id (th/decode-body (app (-> (mock/request :post "/api/peladas")
                                                (mock/json-body {:organization_id org-id
                                                                 :scheduled_at "2026-09-25T19:00:00Z"
                                                                 :location "Campo Teste"})
                                                (th/auth-cookie admin-token)))))
        ;; The creator is already an org player; five more members join.
        admin-player-id (th/player-id-by-user-id ds admin-user-id org-id)
        player-ids (into [admin-player-id]
                         (for [i (range 5)]
                           (let [email (str "prev_p" i "@test.com")]
                             (th/register-and-login! app {:name (str "Prev Player " i) :email email :password "pass123"})
                             (add-member! ds org-id (th/user-id-by-email ds email)))))
        _ (doseq [[idx pid] (map-indexed vector player-ids)]
            (exec! ds (-> (h/insert-into :Attendance)
                          (h/values [{:pelada_id (parse-uuid pelada-id)
                                      :player_id pid
                                      :status [:cast "confirmed" :attendance_status]
                                      :updated_at [[:cast (str "2026-09-2" idx " 10:00:00") :timestamp]]}]))))
        response (app (-> (mock/request :get (str "/api/users/" admin-user-id "/peladas"))
                          (th/auth-cookie admin-token)))
        body (th/decode-body response)
        pelada (first (filter #(= pelada-id (:id %)) body))
        preview-names (when (:confirmed_preview pelada)
                        (str/split (:confirmed_preview pelada) #"\|"))
        earliest-four #{"Preview Admin" "Prev Player 0" "Prev Player 1" "Prev Player 2"}]

    (testing "the user pelada list reports the confirmed headcount"
      (is (= 200 (:status response)))
      (is (= 6 (:confirmed_count pelada))))

    (testing "the preview lists exactly the first four confirmed players"
      (is (= 4 (count preview-names)))
      (is (= earliest-four (set preview-names))))
    (testing "location travels through the list endpoint too"
      (is (= "Campo Teste" (:location pelada))))))

(deftest pelada-confirmed-preview-ordering-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        admin-token (th/register-and-login! app {:name "Order Admin" :email "order_admin@test.com" :password "pass123"})
        admin-user-id (th/user-id-by-email ds "order_admin@test.com")
        org-id (parse-uuid (:id (th/decode-body (app (-> (mock/request :post "/api/organizations")
                                                         (mock/json-body {:name "Org Preview Order"})
                                                         (th/auth-cookie admin-token))))))
        pelada-id (:id (th/decode-body (app (-> (mock/request :post "/api/peladas")
                                                (mock/json-body {:organization_id org-id
                                                                 :scheduled_at "2026-09-25T19:00:00Z"})
                                                (th/auth-cookie admin-token)))))
        ;; Guests and casuals confirm first; monthly members confirm later.
        confirmations [["Guest Early" "convidado"]
                       ["Casual Early" "diarista"]
                       ["Monthly A|B" "mensalista"]
                       ["Monthly Temp" "mensalista_temporario"]
                       ["Monthly Late" "mensalista"]]
        _ (doseq [[idx [player-name member-type]] (map-indexed vector confirmations)]
            (let [email (str "order_p" idx "@test.com")]
              (th/register-and-login! app {:name player-name :email email :password "pass123"})
              (exec! ds (-> (h/insert-into :Attendance)
                            (h/values [{:pelada_id (parse-uuid pelada-id)
                                        :player_id (add-member! ds org-id (th/user-id-by-email ds email) member-type)
                                        :status [:cast "confirmed" :attendance_status]
                                        :updated_at [[:cast (str "2026-09-2" idx " 10:00:00") :timestamp]]}])))))
        body (th/decode-body (app (-> (mock/request :get (str "/api/users/" admin-user-id "/peladas"))
                                      (th/auth-cookie admin-token))))
        pelada (first (filter #(= pelada-id (:id %)) body))
        preview-names (str/split (:confirmed_preview pelada) #"\|")]

    (testing "the preview follows the attendance roster order: member type priority, then FIFO"
      (is (= ["Monthly A B" "Monthly Temp" "Monthly Late" "Casual Early"] preview-names)))

    (testing "a pipe inside a player's name does not break the delimited preview"
      (is (= 4 (count preview-names))))))

(deftest pelada-location-access-test
  (let [app (-> th/*test-system* :app :app-handler)
        admin-token (th/register-and-login! app {:name "Loc Owner" :email "loc_owner@test.com" :password "pass123"})
        outsider-token (th/register-and-login! app {:name "Loc Outsider" :email "loc_outsider@test.com" :password "pass123"})
        org-id (:id (th/decode-body (app (-> (mock/request :post "/api/organizations")
                                             (mock/json-body {:name "Org Loc Access"})
                                             (th/auth-cookie admin-token)))))
        pelada-id (:id (th/decode-body (app (-> (mock/request :post "/api/peladas")
                                                (mock/json-body {:organization_id org-id
                                                                 :scheduled_at "2026-09-26T19:00:00Z"
                                                                 :location "Arena Secreta"})
                                                (th/auth-cookie admin-token)))))]

    (testing "outsiders cannot read pelada details"
      (let [response (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/full-details"))
                              (th/auth-cookie outsider-token)))]
        (is (= 403 (:status response)))))

    (testing "outsiders cannot update the location"
      (let [response (app (-> (mock/request :put (str "/api/peladas/" pelada-id))
                              (mock/json-body {:location "Hack"})
                              (th/auth-cookie outsider-token)))]
        (is (= 403 (:status response)))))

    (testing "members other than admins cannot create peladas"
      (let [response (app (-> (mock/request :post "/api/peladas")
                              (mock/json-body {:organization_id org-id
                                               :scheduled_at "2026-09-27T19:00:00Z"})
                              (th/auth-cookie outsider-token)))]
        (is (= 403 (:status response)))))))

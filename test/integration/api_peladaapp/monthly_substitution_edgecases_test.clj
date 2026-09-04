(ns integration.api-peladaapp.monthly-substitution-edgecases-test
  (:require
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is use-fixtures]]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(defn- decode-body [resp]
  (th/decode-body resp))

(deftest substitution-error-cases
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        token (th/register-and-login! app {:name "Admin" :email "admin@ex.com" :password "p"})
        p2-token (th/register-and-login! app {:name "Player 2" :email "p2@ex.com" :password "p"})
        p3-token (th/register-and-login! app {:name "Player 3" :email "p3@ex.com" :password "p"})
        auth (th/auth-cookie token)
        p2-auth (th/auth-cookie p2-token)
        p3-auth (th/auth-cookie p3-token)
        admin-id (th/user-id-by-email ds "admin@ex.com")
        p2-id (th/user-id-by-email ds "p2@ex.com")
        p3-id (th/user-id-by-email ds "p3@ex.com")

        org-resp (app (-> (mock/request :post "/api/organizations")
                          (mock/json-body {:name "Sub Club"})
                          auth))
        org-id (misc/as-uuid (:id (decode-body org-resp)))]

    (is (= 201 (:status org-resp)))

    ;; Invite and accept players 2 and 3
    (app (-> (mock/request :post (str "/api/organizations/" org-id "/invite"))
             (mock/json-body {:email "p2@ex.com" :name "Player 2"})
             auth))
    (app (-> (mock/request :post (str "/api/organizations/" org-id "/invite"))
             (mock/json-body {:email "p3@ex.com" :name "Player 3"})
             auth))

    (let [invites (decode-body (app (-> (mock/request :get "/api/invitations/pending") p2-auth)))
          token (:token (first invites))]
      (app (-> (mock/request :post (str "/api/invitations/" token "/accept")) p2-auth)))

    (let [invites (decode-body (app (-> (mock/request :get "/api/invitations/pending") p3-auth)))
          token (:token (first invites))]
      (app (-> (mock/request :post (str "/api/invitations/" token "/accept")) p3-auth)))

    ;; Fetch organization players and ids
    (let [players (decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/players")) auth)))
          admin-player (first (filter #(= (misc/as-uuid (:user_id %)) admin-id) players))
          admin-player-id (misc/as-uuid (:id admin-player))
          p2-player (first (filter #(= (misc/as-uuid (:user_id %)) p2-id) players))
          p2-player-id (misc/as-uuid (:id p2-player))
          p3-player (first (filter #(= (misc/as-uuid (:user_id %)) p3-id) players))
          p3-player-id (misc/as-uuid (:id p3-player))]

      ;; 1) Cannot create substitution if permanent is not mensalista
      ;; Force admin to non-mensalista and verify API rejects
      (db.player/update-player admin-player-id {:member-type "diarista"} ds)
      (let [bad-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/substitutions"))
                              (mock/json-body {:permanent_player_id admin-player-id
                                               :temporary_player_id p2-player-id
                                               :start_date "2026-05-06"})
                              auth))]
        (is (= 400 (:status bad-resp))))

      ;; Make admin mensalista and create a valid substitution admin <- p2
      (db.player/update-player admin-player-id {:member-type "mensalista"} ds)
      (let [ok-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/substitutions"))
                             (mock/json-body {:permanent_player_id admin-player-id
                                              :temporary_player_id p2-player-id
                                              :start_date "2026-05-06"})
                             auth))]
        (is (= 200 (:status ok-resp))))

      ;; 2) Cannot create another active substitution for same permanent
      (let [conflict-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/substitutions"))
                                   (mock/json-body {:permanent_player_id admin-player-id
                                                    :temporary_player_id p3-player-id
                                                    :start_date "2026-05-07"})
                                   auth))]
        (is (= 400 (:status conflict-resp))))

      ;; 3) Cannot use a temporary who is already substituting someone else
      ;; Make a second mensalista to test
      (db.player/update-player p3-player-id {:member-type "mensalista"} ds)
      ;; Try to make p2 substitute p3 (p2 is already substituting admin)
      (let [temp-conflict (app (-> (mock/request :post (str "/api/organizations/" org-id "/substitutions"))
                                   (mock/json-body {:permanent_player_id p3-player-id
                                                    :temporary_player_id p2-player-id
                                                    :start_date "2026-05-08"})
                                   auth))]
        (is (= 400 (:status temp-conflict))))

      ;; 4) Ending an already ended substitution should return bad request
      (let [subs (decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/substitutions")) auth)))
            sub-id (misc/as-uuid (:id (first subs)))
            end-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/substitutions/" sub-id "/end"))
                              (mock/json-body {:end_date "2026-06-06"})
                              auth))]
        (is (= 200 (:status end-resp)))

        ;; End again
        (let [end-again (app (-> (mock/request :post (str "/api/organizations/" org-id "/substitutions/" sub-id "/end"))
                                 (mock/json-body {:end_date "2026-06-07"})
                                 auth))]
          (is (= 400 (:status end-again))))))))

(deftest monthly-substitutions-history-bug-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        token (th/register-and-login! app {:name "Admin" :email "admin@ex.com" :password "p"})
        p2-token (th/register-and-login! app {:name "Player 2" :email "p2@ex.com" :password "p"})
        auth (th/auth-cookie token)
        p2-auth (th/auth-cookie p2-token)
        admin-id (th/user-id-by-email ds "admin@ex.com")
        p2-id (th/user-id-by-email ds "p2@ex.com")

        org-resp (app (-> (mock/request :post "/api/organizations")
                          (mock/json-body {:name "Sub Club History"})
                          auth))
        org-id (misc/as-uuid (:id (decode-body org-resp)))]

    (is (= 201 (:status org-resp)))

    ;; Enable features: monthly_substitutions and finance_control
    (jdbc/execute! ds [(str "UPDATE \"OrganizationFeatureFlags\" SET monthly_substitutions = TRUE, finance_control = TRUE WHERE organization_id = '" org-id "'")])

    ;; Invite and accept player 2
    (app (-> (mock/request :post (str "/api/organizations/" org-id "/invite"))
             (mock/json-body {:email "p2@ex.com" :name "Player 2"})
             auth))
    (let [invites (decode-body (app (-> (mock/request :get "/api/invitations/pending") p2-auth)))
          token (:token (first invites))]
      (app (-> (mock/request :post (str "/api/invitations/" token "/accept")) p2-auth)))

    (let [players (decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/players")) auth)))
          admin-player (first (filter #(= (misc/as-uuid (:user_id %)) admin-id) players))
          admin-player-id (misc/as-uuid (:id admin-player))
          p2-player (first (filter #(= (misc/as-uuid (:user_id %)) p2-id) players))
          p2-player-id (misc/as-uuid (:id p2-player))]

      ;; Promote admin to mensalista
      (db.player/update-player admin-player-id {:member-type "mensalista"} ds)
      ;; Make player 2 a diarista
      (db.player/update-player p2-player-id {:member-type "diarista"} ds)

      ;; 1. Check June 2026 monthly payments before substitution: only Admin (mensalista) should be there
      (let [resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/finance/monthly-payments") {:year "2026" :month "6"}) auth))
            payments (decode-body resp)]
        (is (= 200 (:status resp)))
        (is (= 1 (count payments)))
        (is (= admin-player-id (misc/as-uuid (:player_id (first payments))))))

      ;; 2. Mark June 2026 payment as paid for Admin
      (let [pay-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/finance/monthly-payments"))
                              (mock/json-body {:player_id admin-player-id :year 2026 :month 6 :paid true})
                              auth))]
        (is (= 200 (:status pay-resp))))

      ;; 3. Check June again to confirm Admin is paid
      (let [payments (decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/finance/monthly-payments") {:year "2026" :month "6"}) auth)))]
        (is (true? (:paid (first payments)))))

      ;; 4. Now, create substitution starting July 1st (Admin substituted by Player 2)
      (let [sub-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/substitutions"))
                              (mock/json-body {:permanent_player_id admin-player-id
                                               :temporary_player_id p2-player-id
                                               :start_date "2026-07-01"})
                              auth))]
        (is (= 200 (:status sub-resp))))

      ;; 5. Verify June (past month) monthly payments:
      ;; It should STILL be Admin, and he should STILL be paid. Player 2 should NOT be there.
      (let [payments (decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/finance/monthly-payments") {:year "2026" :month "6"}) auth)))]
        (is (= 1 (count payments)))
        (is (= admin-player-id (misc/as-uuid (:player_id (first payments)))))
        (is (true? (:paid (first payments)))))

      ;; 6. Verify July (current month) monthly payments:
      ;; It should be Player 2 (temporary mensalista), and Admin should NOT be there.
      (let [payments (decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/finance/monthly-payments") {:year "2026" :month "7"}) auth)))]
        (is (= 1 (count payments)))
        (is (= p2-player-id (misc/as-uuid (:player_id (first payments)))))
        (is (false? (:paid (first payments))))))))

(deftest monthly-substitutions-ended-early-month-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        token (th/register-and-login! app {:name "Rafa Lucena" :email "rafa@ex.com" :password "p"})
        p2-token (th/register-and-login! app {:name "Alan" :email "alan@ex.com" :password "p"})
        p3-token (th/register-and-login! app {:name "Mala" :email "mala@ex.com" :password "p"})
        auth (th/auth-cookie token)
        p2-auth (th/auth-cookie p2-token)
        p3-auth (th/auth-cookie p3-token)
        rafa-id (th/user-id-by-email ds "rafa@ex.com")
        alan-id (th/user-id-by-email ds "alan@ex.com")
        mala-id (th/user-id-by-email ds "mala@ex.com")

        org-resp (app (-> (mock/request :post "/api/organizations")
                          (mock/json-body {:name "Sub Club Early Month"})
                          auth))
        org-id (misc/as-uuid (:id (decode-body org-resp)))]

    (is (= 201 (:status org-resp)))

    ;; Enable features: monthly_substitutions and finance_control
    (jdbc/execute! ds [(str "UPDATE \"OrganizationFeatureFlags\" SET monthly_substitutions = TRUE, finance_control = TRUE WHERE organization_id = '" org-id "'")])

    ;; Invite and accept alan and mala
    (app (-> (mock/request :post (str "/api/organizations/" org-id "/invite"))
             (mock/json-body {:email "alan@ex.com" :name "Alan"})
             auth))
    (app (-> (mock/request :post (str "/api/organizations/" org-id "/invite"))
             (mock/json-body {:email "mala@ex.com" :name "Mala"})
             auth))

    (let [invites (decode-body (app (-> (mock/request :get "/api/invitations/pending") p2-auth)))
          inv-token (:token (first invites))]
      (app (-> (mock/request :post (str "/api/invitations/" inv-token "/accept")) p2-auth)))

    (let [invites (decode-body (app (-> (mock/request :get "/api/invitations/pending") p3-auth)))
          inv-token (:token (first invites))]
      (app (-> (mock/request :post (str "/api/invitations/" inv-token "/accept")) p3-auth)))

    (let [players (decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/players")) auth)))
          rafa-player (first (filter #(= (misc/as-uuid (:user_id %)) rafa-id) players))
          rafa-player-id (misc/as-uuid (:id rafa-player))
          alan-player (first (filter #(= (misc/as-uuid (:user_id %)) alan-id) players))
          alan-player-id (misc/as-uuid (:id alan-player))
          mala-player (first (filter #(= (misc/as-uuid (:user_id %)) mala-id) players))
          mala-player-id (misc/as-uuid (:id mala-player))]

      ;; Rafa and Mala are mensalistas, Alan is diarista
      (db.player/update-player rafa-player-id {:member-type "mensalista"} ds)
      (db.player/update-player mala-player-id {:member-type "mensalista"} ds)
      (db.player/update-player alan-player-id {:member-type "diarista"} ds)

      ;; Step 1: Alan substitutes Rafa starting in August
      (let [sub1-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/substitutions"))
                               (mock/json-body {:permanent_player_id rafa-player-id
                                                :temporary_player_id alan-player-id
                                                :start_date "2026-08-01"})
                               auth))]
        (is (= 200 (:status sub1-resp))))

      (let [subs (decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/substitutions")) auth)))
            sub1-id (misc/as-uuid (:id (first subs)))]

        ;; Step 2: On September 4th, Rafa returns and sub1 is ended (end_date = 2026-09-04)
        (let [end-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/substitutions/" sub1-id "/end"))
                                (mock/json-body {:end_date "2026-09-04"})
                                auth))]
          (is (= 200 (:status end-resp))))

        ;; Step 3: On September 4th, Alan substitutes Mala (start_date = 2026-09-04)
        (let [sub2-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/substitutions"))
                                 (mock/json-body {:permanent_player_id mala-player-id
                                                  :temporary_player_id alan-player-id
                                                  :start_date "2026-09-04"})
                                 auth))]
          (is (= 200 (:status sub2-resp))))

        ;; Step 4: Verify September 2026 finance
        ;; Rafa (returned mensalista) and Alan (substituting Mala) should BOTH be listed!
        ;; Mala should NOT be listed (he is substituted).
        ;; Total mensalistas must be 2.
        (let [payments-resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/finance/monthly-payments") {:year "2026" :month "9"}) auth))
              payments (decode-body payments-resp)
              player-ids (set (map #(misc/as-uuid (:player_id %)) payments))]
          (is (= 200 (:status payments-resp)))
          (is (= 2 (count payments)))
          (is (contains? player-ids rafa-player-id))
          (is (contains? player-ids alan-player-id))
          (is (not (contains? player-ids mala-player-id))))))))
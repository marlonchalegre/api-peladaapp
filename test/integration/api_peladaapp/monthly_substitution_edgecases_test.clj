(ns integration.api-peladaapp.monthly-substitution-edgecases-test
  (:require
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is use-fixtures]]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(defn- decode-body [resp]
  (th/decode-body resp))

(deftest substitution-error-cases
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
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
        org-id (:id (decode-body org-resp))]

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
          admin-player (first (filter #(= (:user_id %) admin-id) players))
          admin-player-id (:id admin-player)
          p2-player (first (filter #(= (:user_id %) p2-id) players))
          p2-player-id (:id p2-player)
          p3-player (first (filter #(= (:user_id %) p3-id) players))
          p3-player-id (:id p3-player)]

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
            sub-id (:id (first subs))
            end-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/substitutions/" sub-id "/end"))
                              (mock/json-body {:end_date "2026-06-06"})
                              auth))]
        (is (= 200 (:status end-resp)))

        ;; End again
        (let [end-again (app (-> (mock/request :post (str "/api/organizations/" org-id "/substitutions/" sub-id "/end"))
                                 (mock/json-body {:end_date "2026-06-07"})
                                 auth))]
          (is (= 400 (:status end-again))))))))
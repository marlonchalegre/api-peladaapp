(ns integration.api-peladaapp.monthly-substitution-test
  (:require
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.test-helpers :as th]
   [clojure.string :as str]
   [clojure.test :refer [deftest is use-fixtures]]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(defn- decode-body [resp]
  (th/decode-body resp))

(deftest monthly-substitution-integration-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        token (th/register-and-login! app {:name "Admin" :email "admin@ex.com" :password "p"})
        p2-token (th/register-and-login! app {:name "Player 2" :email "p2@ex.com" :password "p"})
        auth (th/auth-cookie token)
        p2-auth (th/auth-cookie p2-token)
        admin-id (th/user-id-by-email ds "admin@ex.com")
        p2-id (th/user-id-by-email ds "p2@ex.com")

        ;; Create organization
        org-resp (app (-> (mock/request :post "/api/organizations")
                          (mock/json-body {:name "Sub Club"})
                          auth))
        org-id (:id (decode-body org-resp))]

    (is (= 201 (:status org-resp)))

    ;; Make admin a mensalista
    (let [players (decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/players")) auth)))
          admin-player (first (filter #(= (:user_id %) admin-id) players))
          admin-player-id (:id admin-player)]
      (db.player/update-player admin-player-id {:member-type "mensalista"} ds)

      ;; Add Player 2 to organization as diarista
      (app (-> (mock/request :post (str "/api/organizations/" org-id "/invite"))
               (mock/json-body {:email "p2@ex.com" :name "Player 2"})
               auth))

      ;; Accept invitation for Player 2
      (let [invites (decode-body (app (-> (mock/request :get "/api/invitations/pending") p2-auth)))
            token (:token (first invites))]
        (app (-> (mock/request :post (str "/api/invitations/" token "/accept")) p2-auth)))

      (let [players (decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/players")) auth)))
            p2-player (first (filter #(= (:user_id %) p2-id) players))
            p2-player-id (:id p2-player)

            ;; Test: Create substitution
            sub-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/substitutions"))
                              (mock/json-body {:permanent_player_id admin-player-id
                                               :temporary_player_id p2-player-id
                                               :start_date "2026-05-06"})
                              auth))]
        (is (= 200 (:status sub-resp)))

        ;; Verify statuses changed
        (let [players-after (decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/players")) auth)))
              admin-after (first (filter #(= (:id %) admin-player-id) players-after))
              p2-after (first (filter #(= (:id %) p2-player-id) players-after))]
          (is (= "diarista_temporario" (:member_type admin-after)))
          (is (= "mensalista_temporario" (:member_type p2-after))))

        ;; Verify substitution listed
        (let [subs (decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/substitutions")) auth)))]
          (is (= 1 (count subs)))
          (is (= admin-player-id (:permanent_player_id (first subs))))
          (is (= p2-player-id (:temporary_player_id (first subs))))
          (is (:active (first subs)))

          ;; Test: End substitution
          (let [sub-id (:id (first subs))
                end-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/substitutions/" sub-id "/end"))
                                  (mock/json-body {:end_date "2026-06-06"})
                                  auth))]
            (is (= 200 (:status end-resp)))

            ;; Verify statuses reverted
            (let [players-final (decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/players")) auth)))
                  admin-final (first (filter #(= (:id %) admin-player-id) players-final))
                  p2-final (first (filter #(= (:id %) p2-player-id) players-final))]
              (is (= "mensalista" (:member_type admin-final)))
              (is (= "diarista" (:member_type p2-final))))

            ;; Verify substitution inactive
            (let [subs-final (decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/substitutions")) auth)))]
              (is (not (:active (first subs-final))))
              (is (str/starts-with? (:end_date (first subs-final)) "2026-06-06")))))))))

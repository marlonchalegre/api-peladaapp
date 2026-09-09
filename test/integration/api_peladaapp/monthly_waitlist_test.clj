(ns integration.api-peladaapp.monthly-waitlist-test
  (:require
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is use-fixtures]]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest monthly-waitlist-full-flow-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        token (th/register-and-login! app {:name "Admin" :email "admin@waitlist.com" :password "secret"})
        p2-token (th/register-and-login! app {:name "Player 2" :email "p2@waitlist.com" :password "secret"})
        p3-token (th/register-and-login! app {:name "Player 3" :email "p3@waitlist.com" :password "secret"})
        auth (th/auth-cookie token)
        p2-auth (th/auth-cookie p2-token)
        p3-auth (th/auth-cookie p3-token)
        admin-id (th/user-id-by-email ds "admin@waitlist.com")
        p2-id (th/user-id-by-email ds "p2@waitlist.com")
        p3-id (th/user-id-by-email ds "p3@waitlist.com")

        ;; Create organization
        org-resp (app (-> (mock/request :post "/api/organizations")
                          (mock/json-body {:name "Waitlist Org"})
                          auth))
        org-id (misc/as-uuid (:id (th/decode-body org-resp)))]

    (is (= 201 (:status org-resp)))

    ;; Make admin a mensalista
    (let [players (th/decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/players")) auth)))
          admin-player (first (filter #(= (misc/as-uuid (:user_id %)) admin-id) players))
          admin-player-id (misc/as-uuid (:id admin-player))]
      (db.player/update-player admin-player-id {:member-type "mensalista"} ds)

      ;; Add Player 2 to organization as diarista
      (app (-> (mock/request :post (str "/api/organizations/" org-id "/invite"))
               (mock/json-body {:email "p2@waitlist.com" :name "Player 2"})
               auth))
      (let [invites (th/decode-body (app (-> (mock/request :get "/api/invitations/pending") p2-auth)))
            invite-token (:token (first invites))]
        (app (-> (mock/request :post (str "/api/invitations/" invite-token "/accept")) p2-auth)))

      ;; Add Player 3 to organization as diarista
      (app (-> (mock/request :post (str "/api/organizations/" org-id "/invite"))
               (mock/json-body {:email "p3@waitlist.com" :name "Player 3"})
               auth))
      (let [invites (th/decode-body (app (-> (mock/request :get "/api/invitations/pending") p3-auth)))
            invite-token (:token (first invites))]
        (app (-> (mock/request :post (str "/api/invitations/" invite-token "/accept")) p3-auth)))

      (let [players (th/decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/players")) auth)))
            p2-player (first (filter #(= (misc/as-uuid (:user_id %)) p2-id) players))
            p2-player-id (misc/as-uuid (:id p2-player))
            p3-player (first (filter #(= (misc/as-uuid (:user_id %)) p3-id) players))
            p3-player-id (misc/as-uuid (:id p3-player))]

        ;; Initial check for Player 2 waitlist status: false
        (let [status-resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/monthly-waitlist/me"))
                                   p2-auth))]
          (is (= 200 (:status status-resp)))
          (is (= false (:in_queue (th/decode-body status-resp)))))

        ;; Player 2 candidates themselves
        (let [join-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/monthly-waitlist"))
                                 (mock/json-body {})
                                 p2-auth))]
          (is (= 201 (:status join-resp))))

        ;; Player 2 status check is now in_queue: true
        (let [status-resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/monthly-waitlist/me"))
                                   p2-auth))]
          (is (= 200 (:status status-resp)))
          (is (= true (:in_queue (th/decode-body status-resp)))))

        ;; Player 2 cannot join again
        (let [dup-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/monthly-waitlist"))
                                (mock/json-body {})
                                p2-auth))]
          (is (= 400 (:status dup-resp))))

        ;; Non-admin cannot list waitlist
        (let [list-forbidden (app (-> (mock/request :get (str "/api/organizations/" org-id "/monthly-waitlist"))
                                      p2-auth))]
          (is (= 403 (:status list-forbidden))))

        ;; Admin adds Player 3 to waitlist
        (let [admin-add (app (-> (mock/request :post (str "/api/organizations/" org-id "/monthly-waitlist"))
                                 (mock/json-body {:player_id p3-player-id})
                                 auth))]
          (is (= 201 (:status admin-add))))

        ;; Admin lists waitlist -> should have 2 entries in FIFO order (p2 first, p3 second)
        (let [list-resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/monthly-waitlist"))
                                 auth))
              waitlist (th/decode-body list-resp)]
          (is (= 200 (:status list-resp)))
          (is (= 2 (count waitlist)))
          (is (= (str p2-player-id) (str (:player_id (first waitlist)))))
          (is (= (str p3-player-id) (str (:player_id (second waitlist))))))

        ;; Non-admin cannot remove another player
        (let [bad-delete (app (-> (mock/request :delete (str "/api/organizations/" org-id "/monthly-waitlist/" p3-player-id))
                                  p2-auth))]
          (is (= 403 (:status bad-delete))))

        ;; Admin promotes Player 3 directly (even though Player 3 was second in line)
        (let [promote-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/monthly-waitlist/" p3-player-id "/promote"))
                                    auth))]
          (is (= 200 (:status promote-resp))))

        ;; Player 3 is now a mensalista!
        (let [p3-updated (db.player/get-player p3-player-id ds)]
          (is (= "mensalista" (:member-type p3-updated))))

        ;; Waitlist now only has Player 2
        (let [list-resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/monthly-waitlist"))
                                 auth))
              waitlist (th/decode-body list-resp)]
          (is (= 1 (count waitlist)))
          (is (= (str p2-player-id) (str (:player_id (first waitlist))))))

        ;; Player 2 leaves the waitlist themselves
        (let [leave-resp (app (-> (mock/request :delete (str "/api/organizations/" org-id "/monthly-waitlist/" p2-player-id))
                                  p2-auth))]
          (is (= 200 (:status leave-resp))))

        ;; Waitlist is now completely empty
        (let [list-resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/monthly-waitlist"))
                                 auth))
              waitlist (th/decode-body list-resp)]
          (is (= 0 (count waitlist))))

        ;; Player 3 (now mensalista) cannot be added to waitlist
        (let [mensalista-add (app (-> (mock/request :post (str "/api/organizations/" org-id "/monthly-waitlist"))
                                      (mock/json-body {:player_id p3-player-id})
                                      auth))]
          (is (= 400 (:status mensalista-add))))))))

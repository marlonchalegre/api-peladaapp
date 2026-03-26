(ns api-peladaapp.vote-test
  (:require
   [api-peladaapp.controllers.vote :as controller.vote]
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.organization :as db.org]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [next.jdbc.sql :as sql]
   [api-peladaapp.helpers.misc :as misc]))

(use-fixtures :each th/test-system-fixture)

(defn- get-id [res]
  (if (map? res)
    (-> res vals first)
    res))

(deftest get-voting-info-admin-test
  (let [db-comp (:database th/*test-system*)
        db-val (:database db-comp)
        ds (if (fn? db-val) (db-val) db-val)
        admin-email "admin@example.com"
        player-email "player@example.com"]

    ;; 1. Create users
    (db.user/insert-user {:name "Admin" :username "admin" :email admin-email :password "pass"} ds)
    (db.user/insert-user {:name "Player" :username "player" :email player-email :password "pass"} ds)
    
    (let [admin-user-id (get-id (db.user/find-user-by-identifier admin-email ds))
          player-user-id (get-id (db.user/find-user-by-identifier player-email ds))
          
          ;; 2. Create organization
          org-id (db.org/insert-organization {:name "Org"} ds)
          
          ;; 3. Make user an admin
          _ (db.admin/insert-organization-admin {:organization-id org-id :user-id admin-user-id} ds)
          
          ;; 4. Add BOTH to organization as players
          admin-player-id (db.player/insert-player {:organization-id org-id :user-id admin-user-id} ds)
          player-id (db.player/insert-player {:organization-id org-id :user-id player-user-id} ds)
          
          ;; 5. Create pelada and close it
          pelada-id (db.pelada/insert-pelada {:organization-id org-id :name "Pelada" :scheduled-at "2026-03-26T10:00:00Z"} ds)]
      
      (db.pelada/update-pelada pelada-id {:status "closed" :closed-at "2026-03-26T12:00:00Z"} ds)
      
      ;; 6. Add ONLY THE PLAYER to a team in the pelada (Admin did not participate)
      (let [team-id (get-id (sql/insert! ds :teams {:pelada_id pelada-id :name "Team A"}))]
        (sql/insert! ds :teamplayers {:team_id team-id :player_id player-id}))

      (testing "Admin (non-participant) should be able to get voting info"
        (let [info (controller.vote/get-voting-info pelada-id admin-user-id ds)]
          (is (some? info))
          (is (false? (:can-vote info)))
          (is (seq (:eligible-players info)))
          (is (= 1 (count (:eligible-players info)))))))))

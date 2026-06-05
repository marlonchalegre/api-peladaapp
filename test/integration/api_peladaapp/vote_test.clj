(ns api-peladaapp.vote-test
  (:require
   [api-peladaapp.controllers.vote :as controller.vote]
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.attendance :as db.attendance]
   [api-peladaapp.db.organization :as db.org]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.team :as db.team]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.db.vote :as db.vote]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs])
  (:import
   [java.time Duration Instant]))

(use-fixtures :each th/test-system-fixture)

(defn- exec-one! [ds query]
  (jdbc/execute-one! ds (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps}))

(defn- exec! [ds query]
  (jdbc/execute! ds (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps}))

(deftest get-voting-info-admin-test
  (let [db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        admin-email "admin@example.com"
        player-email "player@example.com"

        ;; 1. Create users and capture IDs
        admin-user-id (db.user/insert-user {:name "Admin" :username "admin" :email admin-email :password "pass"} ds)
        player-user-id (db.user/insert-user {:name "Player" :username "player" :email player-email :password "pass"} ds)

        ;; 2. Create organization
        org-id (db.org/insert-organization {:name "Org"} ds)

        ;; 3. Make user an admin
        _ (db.admin/insert-organization-admin {:organization-id org-id :user-id admin-user-id} ds)

        ;; 4. Add BOTH to organization as players
        _ (db.player/insert-player {:organization-id org-id :user-id admin-user-id :grade 5.0} ds)
        player-id (db.player/insert-player {:organization-id org-id :user-id player-user-id :grade 5.0} ds)

        ;; 5. Create pelada and close it
        now (Instant/now)
        closed-at (str (.minus now (Duration/ofHours 2)))
        pelada-id (db.pelada/insert-pelada {:organization-id org-id :name "Pelada" :scheduled-at (str (.minus now (Duration/ofHours 4)))} ds)]

    (db.pelada/update-pelada pelada-id {:status "closed" :closed-at closed-at} ds)

    (let [team-resp (exec-one! ds (-> (h/insert-into :Teams)
                                      (h/values [{:pelada_id (misc/as-uuid pelada-id) :name "Team A"}])
                                      (h/returning :id)))
          team-id (:id team-resp)]
      (exec! ds (-> (h/insert-into :TeamPlayers)
                    (h/values [{:team_id (misc/as-uuid team-id) :player_id (misc/as-uuid player-id)}])))
      (let [tp (exec! ds (-> (h/select :*) (h/from :TeamPlayers)))]
        (is (= 1 (count tp)))))

    (testing "Admin (non-participant) should be able to get voting info"
      (let [info (controller.vote/get-voting-info pelada-id admin-user-id ds)]
        (is (some? info))
        (is (false? (:can-vote info)))
        (is (seq (:eligible-players info)))
        (is (= 1 (count (:eligible-players info))))))))

(deftest vote-with-fixed-goalkeepers-test
  (let [db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        admin-email "admin@example.com"
        player-email "player@example.com"
        gk-email "gk@example.com"

        ;; 1. Create users and capture IDs
        admin-user-id (db.user/insert-user {:name "Admin" :username "admin" :email admin-email :password "pass"} ds)
        player-user-id (db.user/insert-user {:name "Player" :username "player" :email player-email :password "pass"} ds)
        gk-user-id (db.user/insert-user {:name "Goalkeeper" :username "gk" :email gk-email :password "pass"} ds)

        ;; 2. Create organization
        org-id (db.org/insert-organization {:name "Org"} ds)

        ;; 3. Make admin user an admin
        _ (db.admin/insert-organization-admin {:organization-id org-id :user-id admin-user-id} ds)

        ;; 4. Add all to organization as players
        _ (db.player/insert-player {:organization-id org-id :user-id admin-user-id :grade 5.0} ds)
        player-id (db.player/insert-player {:organization-id org-id :user-id player-user-id :grade 5.0} ds)
        gk-id (db.player/insert-player {:organization-id org-id :user-id gk-user-id :grade 5.0} ds)

        ;; 5. Create pelada with gk-id as home_fixed_goalkeeper_id
        now (Instant/now)
        closed-at (str (.minus now (Duration/ofHours 2)))
        pelada-id (db.pelada/insert-pelada {:organization-id org-id
                                            :name "Pelada"
                                            :scheduled-at (str (.minus now (Duration/ofHours 4)))
                                            :fixed-goalkeepers true
                                            :home-fixed-goalkeeper-id gk-id} ds)]

    (db.pelada/update-pelada pelada-id {:status "closed" :closed-at closed-at} ds)

    ;; 6. Create teams and add players (only player-id is in TeamPlayers)
    (let [team-resp (exec-one! ds (-> (h/insert-into :Teams)
                                      (h/values [{:pelada_id (misc/as-uuid pelada-id) :name "Team A"}])
                                      (h/returning :id)))
          team-id (:id team-resp)]
      (exec! ds (-> (h/insert-into :TeamPlayers)
                    (h/values [{:team_id (misc/as-uuid team-id) :player_id (misc/as-uuid player-id)}]))))

    (testing "Verification of fixed goalkeeper participation and eligibility"
      (is (true? (db.team/did-player-participate-in-pelada? pelada-id gk-id ds)))
      (is (true? (db.team/did-player-participate-in-pelada? pelada-id player-id ds)))
      (is (false? (db.team/did-player-participate-in-pelada? pelada-id admin-user-id ds)))

      (let [participants (db.vote/list-pelada-participants pelada-id ds)
            participant-ids (set (map :player_id participants))]
        (is (= 2 (count participants)))
        (is (contains? participant-ids gk-id))
        (is (contains? participant-ids player-id)))

      (let [eligible-gk (db.vote/list-eligible-players-for-voting pelada-id gk-id ds)
            eligible-gk-ids (set (map :player_id eligible-gk))]
        (is (= 1 (count eligible-gk)))
        (is (contains? eligible-gk-ids player-id))
        (is (not (contains? eligible-gk-ids gk-id)))

        (let [eligible-player (db.vote/list-eligible-players-for-voting pelada-id player-id ds)
              eligible-player-ids (set (map :player_id eligible-player))]
          (is (= 1 (count eligible-player)))
          (is (contains? eligible-player-ids gk-id))
          (is (not (contains? eligible-player-ids player-id))))))

    (testing "Fixed goalkeeper is blocked by default"
      (let [info (controller.vote/get-voting-info pelada-id gk-user-id ds)]
        (is (some? info))
        (is (false? (:can-vote info)))))

    (testing "Admin unblocks fixed goalkeeper"
      (db.attendance/update-voting-enabled pelada-id gk-id true ds))

    (testing "Fixed goalkeeper should be able to get voting info and vote after unblock"
      (let [info (controller.vote/get-voting-info pelada-id gk-user-id ds)]
        (is (some? info))
        (is (true? (:can-vote info)))
        (is (false? (:has-voted info)))
        (is (= 1 (count (:eligible-players info))))
        (is (= (str player-id) (str (:player-id (first (:eligible-players info))))))))))

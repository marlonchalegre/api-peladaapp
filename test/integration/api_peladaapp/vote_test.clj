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
          ;; player-id is in Team A with Home Fixed GK, so gk-id is eligible as teammate
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

(deftest vote-team-only-and-privacy-test
  (let [db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        admin-email "admin@test.com"
        p1-email "p1@test.com"
        p2-email "p2@test.com"
        p3-email "p3@test.com"

        ;; Users
        admin-user-id (db.user/insert-user {:name "Admin" :username "admin-vote" :email admin-email :password "pass"} ds)
        p1-user-id (db.user/insert-user {:name "P1" :username "p1" :email p1-email :password "pass"} ds)
        p2-user-id (db.user/insert-user {:name "P2" :username "p2" :email p2-email :password "pass"} ds)
        p3-user-id (db.user/insert-user {:name "P3" :username "p3" :email p3-email :password "pass"} ds)

        ;; Org
        org-id (db.org/insert-organization {:name "Org"} ds)
        _ (db.admin/insert-organization-admin {:organization-id org-id :user-id admin-user-id} ds)

        ;; Players
        p1-id (db.player/insert-player {:organization-id org-id :user-id p1-user-id :grade 5.0} ds)
        p2-id (db.player/insert-player {:organization-id org-id :user-id p2-user-id :grade 5.0} ds)
        p3-id (db.player/insert-player {:organization-id org-id :user-id p3-user-id :grade 5.0} ds)

        ;; Pelada closed 2 hours ago
        now (Instant/now)
        closed-at (str (.minus now (Duration/ofHours 2)))
        pelada-id (db.pelada/insert-pelada {:organization-id org-id
                                            :name "Pelada Team Vote"
                                            :scheduled-at (str (.minus now (Duration/ofHours 4)))} ds)]

    (db.pelada/update-pelada pelada-id {:status "closed" :closed-at closed-at} ds)

    ;; Team A: P1 and P2
    (let [team-a-resp (exec-one! ds (-> (h/insert-into :Teams)
                                        (h/values [{:pelada_id (misc/as-uuid pelada-id) :name "Team A"}])
                                        (h/returning :id)))
          team-a-id (:id team-a-resp)
          team-b-resp (exec-one! ds (-> (h/insert-into :Teams)
                                        (h/values [{:pelada_id (misc/as-uuid pelada-id) :name "Team B"}])
                                        (h/returning :id)))
          team-b-id (:id team-b-resp)]
      (exec! ds (-> (h/insert-into :TeamPlayers)
                    (h/values [{:team_id (misc/as-uuid team-a-id) :player_id (misc/as-uuid p1-id)}
                               {:team_id (misc/as-uuid team-a-id) :player_id (misc/as-uuid p2-id)}
                               {:team_id (misc/as-uuid team-b-id) :player_id (misc/as-uuid p3-id)}]))))

    (testing "P1 should only see teammates (P2) as eligible players, not P3 or himself"
      (let [info (controller.vote/get-voting-info pelada-id p1-user-id ds)]
        (is (true? (:can-vote info)))
        (is (= 1 (count (:eligible-players info))))
        (is (= (str p2-id) (str (:player-id (first (:eligible-players info))))))))

    (testing "P3 is alone in Team B, so has 0 eligible players"
      (let [info (controller.vote/get-voting-info pelada-id p3-user-id ds)]
        (is (true? (:can-vote info)))
        (is (= 0 (count (:eligible-players info))))))

    (testing "P1 casting vote for P2 succeeds"
      (let [res (controller.vote/batch-cast-votes pelada-id p1-id [{:target-id p2-id :stars 5}] ds)]
        (is (= 1 (:votes-cast res)))))

    (testing "P1 casting vote for P3 (from another team) throws error"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Target player is not in voter's team"
                            (controller.vote/batch-cast-votes pelada-id p1-id [{:target-id p3-id :stars 5}] ds))))

    (testing "Privacy: Non-admin calling get-voting-status cannot see who voted"
      (let [status (controller.vote/get-voting-status pelada-id p1-user-id ds)]
        (is (= 3 (:total-eligible status)))
        (is (= 1 (:total-voted status)))
        (is (empty? (:voters status)))))

    (testing "Privacy: Admin calling get-voting-status CAN see who voted"
      (let [status (controller.vote/get-voting-status pelada-id admin-user-id ds)]
        (is (= 3 (:total-eligible status)))
        (is (= 1 (:total-voted status)))
        (is (= 3 (count (:voters status))))
        (let [voters-map (into {} (map (fn [v] [(:player-id v) (:has-voted v)]) (:voters status)))]
          (is (true? (get voters-map p1-id)))
          (is (false? (get voters-map p2-id)))
          (is (false? (get voters-map p3-id))))))))

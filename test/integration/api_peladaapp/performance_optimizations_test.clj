(ns api-peladaapp.performance-optimizations-test
  (:require
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.attendance :as db.attendance]
   [api-peladaapp.db.match :as db.match]
   [api-peladaapp.db.match-event :as db.match-event]
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.team :as db.team]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.db.vote :as db.vote]
   [api-peladaapp.test-helpers :as h]
   [clojure.test :refer [deftest is use-fixtures]]))

(use-fixtures :each h/test-system-fixture)

(deftest test-list-user-organizations-optimization
  (let [db (h/get-test-datasource)
        u-id (db.user/insert-user {:name "User" :email "u@test.com" :password "p"} db)
        org1-id (db.organization/insert-organization {:name "Admin Org"} db)
        org2-id (db.organization/insert-organization {:name "Player Org"} db)]
    ;; Setup Admin Role
    (db.admin/insert-organization-admin {:organization-id org1-id :user-id u-id} db)

    ;; Setup Player Role
    (db.player/insert-player {:user-id u-id :organization-id org2-id :grade 5.0 :position-id 1} db)

    (let [orgs (db.organization/list-by-user u-id db)
          org-map (group-by :id orgs)]
      (is (= 2 (count orgs)))
      (is (= "admin" (:role (first (get org-map org1-id)))))
      (is (= "player" (:role (first (get org-map org2-id))))))))

(deftest test-upsert-attendance-optimization
  (let [db (h/get-test-datasource)
        u-id (db.user/insert-user {:name "User" :email "u@test.com" :password "p"} db)
        org-id (db.organization/insert-organization {:name "Org"} db)
        p-id (db.player/insert-player {:user-id u-id :organization-id org-id :grade 5.0 :position-id 1} db)
        pelada-id (db.pelada/insert-pelada {:organization-id org-id :scheduled-at "2023-01-01T10:00:00"} db)]

    ;; First insert
    (db.attendance/upsert-attendance pelada-id p-id "confirmed" db)
    (let [att (first (db.attendance/list-attendance-by-pelada pelada-id db))]
      (is (= "confirmed" (:status att))))

    ;; Update (Upsert)
    (db.attendance/upsert-attendance pelada-id p-id "declined" db)
    (let [att (first (db.attendance/list-attendance-by-pelada pelada-id db))]
      (is (= "declined" (:status att)))
      (is (= 1 (count (db.attendance/list-attendance-by-pelada pelada-id db)))))))

(deftest test-pelada-player-stats-triggers
  (let [db (h/get-test-datasource)
        u-id (db.user/insert-user {:name "User" :email "u@test.com" :password "p"} db)
        org-id (db.organization/insert-organization {:name "Org"} db)
        p-id (db.player/insert-player {:user-id u-id :organization-id org-id :grade 5.0 :position-id 1} db)
        pelada-id (db.pelada/insert-pelada {:organization-id org-id :scheduled-at "2023-01-01T10:00:00"} db)
        team1-id (db.team/insert-team {:pelada-id pelada-id :name "T1"} db)
        team2-id (db.team/insert-team {:pelada-id pelada-id :name "T2"} db)
        match-id (db.match/insert-match {:pelada-id pelada-id :home-team-id team1-id :away-team-id team2-id :sequence 1 :status "running" :home-score 0 :away-score 0} db)]

    ;; Insert Goal
    (db.match-event/insert-event match-id p-id "goal" db)
    (let [stats (first (db.match-event/list-player-stats-by-pelada pelada-id db))]
      (is (= 1 (:goals stats)))
      (is (= 0 (:assists stats))))

    ;; Insert Assist
    (db.match-event/insert-event match-id p-id "assist" db)
    (let [stats (first (db.match-event/list-player-stats-by-pelada pelada-id db))]
      (is (= 1 (:goals stats)))
      (is (= 1 (:assists stats))))

    ;; Delete Goal (we need to find the event id to delete properly if using delete-by-id, but the helper uses subquery)
    (db.match-event/delete-last-event match-id p-id "goal" db)
    (let [stats (first (db.match-event/list-player-stats-by-pelada pelada-id db))]
      (is (= 0 (:goals stats)))
      (is (= 1 (:assists stats))))))

(deftest test-batch-vote-insert
  (let [db (h/get-test-datasource)
        ;; Setup users, org, players, pelada...
        u1 (db.user/insert-user {:name "Voter" :email "v@t.com" :password "p"} db)
        u2 (db.user/insert-user {:name "Target1" :email "t1@t.com" :password "p"} db)
        u3 (db.user/insert-user {:name "Target2" :email "t2@t.com" :password "p"} db)
        org (db.organization/insert-organization {:name "Org"} db)
        voter (db.player/insert-player {:user-id u1 :organization-id org :grade 5.0 :position-id 1} db)
        target1 (db.player/insert-player {:user-id u2 :organization-id org :grade 5.0 :position-id 1} db)
        target2 (db.player/insert-player {:user-id u3 :organization-id org :grade 5.0 :position-id 1} db)
        pelada-id (db.pelada/insert-pelada {:organization-id org :scheduled-at "2023-01-01T10:00:00"} db)]

    (db.vote/insert-votes-batch [{:pelada-id pelada-id :voter-id voter :target-id target1 :stars 5}
                                 {:pelada-id pelada-id :voter-id voter :target-id target2 :stars 4}] db)

    (let [votes (db.vote/list-votes-by-pelada pelada-id db)]
      (is (= 2 (count votes))))))

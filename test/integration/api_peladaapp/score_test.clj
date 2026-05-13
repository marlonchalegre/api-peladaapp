(ns api-peladaapp.score-test
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.logic.score :as logic.score]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs])
  (:import
   [java.time LocalDateTime]))

(use-fixtures :each th/test-system-fixture)

(defn- insert! [ds query]
  (jdbc/execute! ds (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps}))

(deftest get-normalized-scores-test
  (let [ds (th/get-test-datasource)]

    ;; Setup Organization
    (insert! ds (-> (h/insert-into :Organizations)
                    (h/values [{:name "Org"}])))

    (let [org-id (-> (jdbc/execute-one! ds (hsql/format (-> (h/select :id)
                                                            (h/from :Organizations)
                                                            (h/where [:= :name "Org"])))
                                        {:builder-fn rs/as-unqualified-lower-maps})
                     :id)]

      ;; Setup Users and OrganizationPlayers
      (doseq [[name email] [["Ana" "ana@example.com"]
                            ["Bob" "bob@example.com"]
                            ["Cid" "cid@example.com"]]]
        (insert! ds (-> (h/insert-into :Users)
                        (h/values [{:name name :email email :password "p"}])))
        (let [user-id (th/user-id-by-email ds email)]
          (insert! ds (-> (h/insert-into :OrganizationPlayers)
                          (h/values [{:organization_id (misc/as-uuid org-id) :user_id (misc/as-uuid user-id)}])))))

      (let [ana-id (th/user-id-by-email ds "ana@example.com")
            bob-id (th/user-id-by-email ds "bob@example.com")
            cid-id (th/user-id-by-email ds "cid@example.com")
            ana-player-id (th/player-id-by-user-id ds ana-id org-id)
            bob-player-id (th/player-id-by-user-id ds bob-id org-id)
            cid-player-id (th/player-id-by-user-id ds cid-id org-id)
            scheduled-at (LocalDateTime/of 2025 10 28 20 0)
            closed-at (LocalDateTime/now)]

        ;; Setup Pelada
        (insert! ds (-> (h/insert-into :Peladas)
                        (h/values [{:organization_id (misc/as-uuid org-id)
                                    :scheduled_at scheduled-at
                                    :status "closed"
                                    :closed_at closed-at}])))

        (let [pelada-id (-> (jdbc/execute-one! ds (hsql/format (-> (h/select :id)
                                                                   (h/from :Peladas)
                                                                   (h/where [:= :organization_id (misc/as-uuid org-id)])))
                                               {:builder-fn rs/as-unqualified-lower-maps})
                            :id)]

          ;; Setup Votes
          (insert! ds (-> (h/insert-into :Votes)
                          (h/values [{:pelada_id (misc/as-uuid pelada-id) :voter_id (misc/as-uuid bob-player-id) :target_id (misc/as-uuid ana-player-id) :stars 5}
                                     {:pelada_id (misc/as-uuid pelada-id) :voter_id (misc/as-uuid cid-player-id) :target_id (misc/as-uuid ana-player-id) :stars 3}
                                     {:pelada_id (misc/as-uuid pelada-id) :voter_id (misc/as-uuid ana-player-id) :target_id (misc/as-uuid bob-player-id) :stars 4}])))

          (testing "Fetches grades for given player IDs"
            (let [player-ids [ana-player-id bob-player-id cid-player-id]
                  scores (logic.score/get-normalized-scores player-ids ds)]
              (is (= 5.0 (get scores ana-player-id))) ;; Default since no grade was set in OrganizationPlayers
              (is (= 5.0 (get scores bob-player-id))) ;; Default
              (is (= 5.0 (get scores cid-player-id)))))

          (testing "Pulls directly from player grade column"
            (insert! ds (-> (h/insert-into :Users)
                            (h/values [{:name "Dani" :email "dani@e.com" :password "p"}])))
            (let [dani-id (th/user-id-by-email ds "dani@e.com")]
              (insert! ds (-> (h/insert-into :OrganizationPlayers)
                              (h/values [{:organization_id (misc/as-uuid org-id) :user_id (misc/as-uuid dani-id) :grade 7.5}])))
              (let [dani-player-id (th/player-id-by-user-id ds dani-id org-id)
                    scores (logic.score/get-normalized-scores [dani-player-id] ds)]
                (is (= 7.5 (get scores dani-player-id)))))))))))

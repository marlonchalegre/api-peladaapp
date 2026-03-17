(ns api-peladaapp.adapter-test
  (:require
   [api-peladaapp.adapters.admin :as adapter.admin]
   [api-peladaapp.adapters.player :as adapter.player]
   [clojure.test :refer [deftest is testing]]))

(deftest player-adapter-test
  (testing "db->model with user-position"
    (let [db-row {:id 1
                  :user_id 2
                  :organization_id 3
                  :grade 5.0
                  :position_id 1
                  :member_type "mensalista"
                  :user_name "John Doe"
                  :user_username "johndoe"
                  :user_email "john@example.com"
                  :user_position "Striker"}
          model (adapter.player/db->model db-row)]
      (is (= "Striker" (:user-position model)))
      (is (= "John Doe" (:user-name model)))
      (is (= "johndoe" (:user-username model)))))

  (testing "model->response with user-position"
    (let [model {:id 1
                 :user-id 2
                 :organization-id 3
                 :grade 5.0
                 :position-id 1
                 :member-type "mensalista"
                 :user-name "John Doe"
                 :user-username "johndoe"
                 :user-email "john@example.com"
                 :user-position "Striker"}
          resp (adapter.player/model->response model)]
      (is (= "Striker" (:user_position resp)))
      (is (= "John Doe" (:user_name resp)))
      (is (= "johndoe" (:user_username resp))))))

(deftest admin-adapter-test
  (testing "db->model with user-position"
    (let [db-row {:id 1
                  :organization_id 10
                  :user_id 20
                  :user_name "Admin User"
                  :user_username "adminuser"
                  :user_email "admin@example.com"
                  :user_position "Goalkeeper"}
          model (adapter.admin/db->model db-row)]
      (is (= "Goalkeeper" (:user-position model)))
      (is (= "Admin User" (:user-name model)))
      (is (= "adminuser" (:user-username model)))))

  (testing "model->response with user-position"
    (let [model {:id 1
                 :organization-id 10
                 :user-id 20
                 :user-name "Admin User"
                 :user-username "adminuser"
                 :user-email "admin@example.com"
                 :user-position "Goalkeeper"}
          resp (adapter.admin/model->response model)]
      (is (= "Goalkeeper" (:user_position resp)))
      (is (= "Admin User" (:user_name resp)))
      (is (= "adminuser" (:user_username resp))))))

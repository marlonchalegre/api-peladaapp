(ns api-peladaapp.adapter-test
  (:require
   [api-peladaapp.adapters.admin :as adapter.admin]
   [api-peladaapp.adapters.player :as adapter.player]
   [clojure.test :refer [deftest is testing]]))

(deftest player-adapter-test
  (testing "db->model with user-position"
    (let [db-row {:id (parse-uuid "00000000-0000-0000-0000-000000000001")
                  :user_id (parse-uuid "00000000-0000-0000-0000-000000000002")
                  :organization_id (parse-uuid "00000000-0000-0000-0000-000000000003")
                  :grade 5.0
                  :position_id (parse-uuid "00000000-0000-0000-0000-000000000004")
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
    (let [model {:id (parse-uuid "00000000-0000-0000-0000-000000000001")
                 :user-id (parse-uuid "00000000-0000-0000-0000-000000000002")
                 :organization-id (parse-uuid "00000000-0000-0000-0000-000000000003")
                 :grade 5.0
                 :position-id (parse-uuid "00000000-0000-0000-0000-000000000004")
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
    (let [db-row {:id (parse-uuid "00000000-0000-0000-0000-000000000001")
                  :organization_id (parse-uuid "00000000-0000-0000-0000-000000000010")
                  :user_id (parse-uuid "00000000-0000-0000-0000-000000000020")
                  :user_name "Admin User"
                  :user_username "adminuser"
                  :user_email "admin@example.com"
                  :user_position "Goalkeeper"}
          model (adapter.admin/db->model db-row)]
      (is (= "Goalkeeper" (:user-position model)))
      (is (= "Admin User" (:user-name model)))
      (is (= "adminuser" (:user-username model)))))

  (testing "model->response with user-position"
    (let [model {:id (parse-uuid "00000000-0000-0000-0000-000000000001")
                 :organization-id (parse-uuid "00000000-0000-0000-0000-000000000010")
                 :user-id (parse-uuid "00000000-0000-0000-0000-000000000020")
                 :user-name "Admin User"
                 :user-username "adminuser"
                 :user-email "admin@example.com"
                 :user-position "Goalkeeper"}
          resp (adapter.admin/model->response model)]
      (is (= "Goalkeeper" (:user_position resp)))
      (is (= "Admin User" (:user_name resp)))
      (is (= "adminuser" (:user_username resp))))))

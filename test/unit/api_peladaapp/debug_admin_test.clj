(ns api-peladaapp.debug-admin-test
  (:require
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.organization :as db.org]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is use-fixtures]]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]))

(use-fixtures :once th/test-system-fixture)

(deftest debug-admin
  (let [ds (th/get-test-datasource)
        u-id (db.user/insert-user {:name "Admin User" :username "admin123" :email "admin@test.com" :password "pass123"} ds)
        o-id (db.org/insert-organization {:name "Test Org" :owner-id u-id} ds)]
    (db.admin/insert-organization-admin {:organization-id o-id :user-id u-id} ds)
    (println "U-ID:" u-id)
    (println "O-ID:" o-id)
    (println "IS ADMIN?" (db.admin/is-user-admin-of-organization? u-id o-id ds))
    (let [admins (jdbc/execute! ds (hsql/format (-> (h/select :*) (h/from :OrganizationAdmins))))]
      (println "ADMINS:" admins))
    (is (= true (db.admin/is-user-admin-of-organization? u-id o-id ds)))))

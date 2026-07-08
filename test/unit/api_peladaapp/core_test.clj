(ns api-peladaapp.core-test
  (:require
   [api-peladaapp.components :as core.components]
   [api-peladaapp.core :as core]
   [clojure.test :refer [deftest is testing]]
   [com.stuartsierra.component :as component]
   [migratus.core :as migratus]
   [next.jdbc]))

(deftest test-add-schema-to-url
  (let [add-schema-to-url #'core/add-schema-to-url]
    (testing "when schema is blank or public, returns url unmodified"
      (is (= "jdbc:postgresql://localhost:5432/db" (add-schema-to-url "jdbc:postgresql://localhost:5432/db" nil)))
      (is (= "jdbc:postgresql://localhost:5432/db" (add-schema-to-url "jdbc:postgresql://localhost:5432/db" "")))
      (is (= "jdbc:postgresql://localhost:5432/db" (add-schema-to-url "jdbc:postgresql://localhost:5432/db" "public"))))

    (testing "when url already contains currentSchema, returns unmodified"
      (is (= "jdbc:postgresql://localhost:5432/db?currentSchema=custom"
             (add-schema-to-url "jdbc:postgresql://localhost:5432/db?currentSchema=custom" "other"))))

    (testing "appends schema using correct separator"
      (is (= "jdbc:postgresql://localhost:5432/db?currentSchema=tenant"
             (add-schema-to-url "jdbc:postgresql://localhost:5432/db" "tenant")))
      (is (= "jdbc:postgresql://localhost:5432/db?ssl=true&currentSchema=tenant"
             (add-schema-to-url "jdbc:postgresql://localhost:5432/db?ssl=true" "tenant"))))))

(deftest test-ensure-global-admin
  (testing "handles database exceptions gracefully without crashing"
    (with-redefs [next.jdbc/execute-one! (fn [& _] (throw (Exception. "Database exception")))]
      (is (nil? (#'core/ensure-global-admin "dummy-db")))))

  (testing "handles existing user path"
    (let [execute-one-called (atom false)
          execute-called (atom false)]
      (with-redefs [next.jdbc/execute-one! (fn [_ _]
                                             (reset! execute-one-called true)
                                             {:id (random-uuid)})
                    next.jdbc/execute! (fn [_ _]
                                         (reset! execute-called true)
                                         {:update-count 1})]
        (#'core/ensure-global-admin "dummy-db")
        (when (System/getenv "SUPER_ADMIN_EMAIL")
          (is @execute-one-called)
          (is @execute-called)))))

  (testing "handles new user creation when email exists"
    (let [execute-one-called (atom false)
          execute-called (atom false)]
      (with-redefs [next.jdbc/execute-one! (fn [_ _]
                                             (reset! execute-one-called true)
                                             nil)
                    next.jdbc/execute! (fn [_ _]
                                         (reset! execute-called true)
                                         {:update-count 1})]
        (#'core/ensure-global-admin "dummy-db")
        (when (and (System/getenv "SUPER_ADMIN_EMAIL")
                   (System/getenv "SUPER_ADMIN_PASSWORD"))
          (is @execute-one-called)
          (is @execute-called))))))

(deftest test-main-workflow
  (testing "-main executes successfully with mocked migrations and component system startup"
    (with-redefs [migratus/migrate (fn [_] nil)
                  core.components/system (fn [_] {})
                  component/start (fn [_] {:database {:database "dummy-db"}})
                  component/stop (fn [_] nil)
                  core/ensure-global-admin (fn [_] nil)]
      (is (nil? (core/-main))))))

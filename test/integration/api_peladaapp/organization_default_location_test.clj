(ns integration.api-peladaapp.organization-default-location-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest organization-default-location-create-and-update-test
  (let [app (-> th/*test-system* :app :app-handler)
        token (th/register-and-login! app {:name "Loc Admin" :email "org_loc_admin@test.com" :password "pass123"})
        create-resp (app (-> (mock/request :post "/api/organizations")
                             (mock/json-body {:name "Org Default Loc"
                                              :default_location "Arena Central · Quadra 1"
                                              :default_max_players 22})
                             (th/auth-cookie token)))
        create-body (th/decode-body create-resp)
        org-id (:id create-body)]

    (testing "creating an organization stores default_location"
      (is (= 201 (:status create-resp)))
      (is (= "Arena Central · Quadra 1" (:default_location create-body)))
      (is (= 22 (:default_max_players create-body))))

    (testing "GET returns the stored default_location"
      (let [get-body (th/decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id))
                                              (th/auth-cookie token))))]
        (is (= "Arena Central · Quadra 1" (:default_location get-body)))))

    (testing "updating default_location persists the new value"
      (let [update-resp (app (-> (mock/request :put (str "/api/organizations/" org-id))
                                 (mock/json-body {:name "Org Default Loc"
                                                  :default_location "Nova Arena · Pista 2"})
                                 (th/auth-cookie token)))
            update-body (th/decode-body update-resp)
            get-body (th/decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id))
                                              (th/auth-cookie token))))]
        (is (= 200 (:status update-resp)))
        (is (= "Nova Arena · Pista 2" (:default_location update-body)))
        (is (= "Nova Arena · Pista 2" (:default_location get-body)))))

    (testing "an update without the default_location key leaves it untouched"
      (let [_ (app (-> (mock/request :put (str "/api/organizations/" org-id))
                       (mock/json-body {:name "Org Default Loc Renamed"})
                       (th/auth-cookie token)))
            get-body (th/decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id))
                                              (th/auth-cookie token))))]
        (is (= "Org Default Loc Renamed" (:name get-body)))
        (is (= "Nova Arena · Pista 2" (:default_location get-body)))))

    (testing "sending an explicit null clears default_location"
      (let [update-resp (app (-> (mock/request :put (str "/api/organizations/" org-id))
                                 (mock/json-body {:name "Org Default Loc Renamed"
                                                  :default_location nil})
                                 (th/auth-cookie token)))
            get-body (th/decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id))
                                              (th/auth-cookie token))))]
        (is (= 200 (:status update-resp)))
        (is (nil? (:default_location get-body)))))))

(deftest organization-default-location-access-test
  (let [app (-> th/*test-system* :app :app-handler)
        owner-token (th/register-and-login! app {:name "Owner Loc" :email "org_loc_owner@test.com" :password "pass123"})
        outsider-token (th/register-and-login! app {:name "Outsider Loc" :email "org_loc_outsider@test.com" :password "pass123"})
        org-id (:id (th/decode-body (app (-> (mock/request :post "/api/organizations")
                                             (mock/json-body {:name "Org Loc Guard"
                                                              :default_location "Arena Original"})
                                             (th/auth-cookie owner-token)))))]

    (testing "outsiders cannot read the organization"
      (let [response (app (-> (mock/request :get (str "/api/organizations/" org-id))
                              (th/auth-cookie outsider-token)))]
        (is (= 403 (:status response)))))

    (testing "outsiders cannot update default_location"
      (let [response (app (-> (mock/request :put (str "/api/organizations/" org-id))
                              (mock/json-body {:name "Org Loc Guard"
                                               :default_location "Hacked Arena"})
                              (th/auth-cookie outsider-token)))]
        (is (= 403 (:status response)))))

    (testing "the original default_location is untouched after the failed update"
      (let [get-body (th/decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id))
                                              (th/auth-cookie owner-token))))]
        (is (= "Arena Original" (:default_location get-body)))))))

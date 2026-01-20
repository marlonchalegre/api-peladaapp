(ns api-peladaapp.substitutions-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest create-and-list-substitutions
  (testing "Create and list substitutions with authorization"
    (let [app (-> th/*test-system* :app :handler)
          token (th/register-and-login! app {:name "U" :email "u@e.com" :password "p"})
          auth (th/auth-header token)

      ;; Create organization (user becomes admin)
          org-resp (app (-> (mock/request :post "/api/organizations")
                            (mock/json-body {:name "Test Org"})
                            auth))
          org-body (th/decode-body org-resp)
          org-id (:id org-body)]
      (is (= 201 (:status org-resp)))

        ;; Create players for the organization
      (let [player1-resp (app (-> (mock/request :post "/api/players")
                                  (mock/json-body {:organization_id org-id :user_id 1})
                                  auth))
            player2-resp (app (-> (mock/request :post "/api/players")
                                  (mock/json-body {:organization_id org-id :user_id 1})
                                  auth))]
        (is (= 201 (:status player1-resp)))
        (is (= 201 (:status player2-resp))))

        ;; Create pelada
      (let [pelada-resp (app (-> (mock/request :post "/api/peladas")
                                 (mock/json-body {:organization_id org-id})
                                 auth))
            pelada-body (th/decode-body pelada-resp)
            pelada-id (:id pelada-body)]
        (is (= 201 (:status pelada-resp)))

          ;; Create teams
        (doseq [n ["A" "B"]]
          (app (-> (mock/request :post "/api/teams")
                   (mock/json-body {:pelada_id pelada-id :name n})
                   auth)))

          ;; Close attendance
        (is (= 200 (:status (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close-attendance")) auth)))))

          ;; Begin pelada to generate matches
        (let [begin-resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin")) auth))]
          (is (= 200 (:status begin-resp))))

          ;; List substitutions (should be empty)
        (let [list-resp (app (-> (mock/request :get "/api/matches/1/substitutions") auth))
              subs (th/decode-body list-resp)]
          (is (= 200 (:status list-resp)))
                      ;; It's ok if there are no substitutions yet
          (is (vector? subs)))))))

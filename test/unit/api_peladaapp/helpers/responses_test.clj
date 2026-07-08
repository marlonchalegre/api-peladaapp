(ns api-peladaapp.helpers.responses-test
  (:require
   [api-peladaapp.helpers.responses :as resp]
   [clojure.test :refer [deftest is testing]]))

(deftest test-responses
  (testing "ok helper"
    (is (= {:status 200 :headers {} :body {:ok true}} (resp/ok {:ok true})))
    (is (= {:status 200 :headers {"x-header" "val"} :body {:ok true}} (resp/ok {:ok true} {"x-header" "val"})))
    (is (= {:status 200 :headers {} :body {}} (resp/ok nil))))

  (testing "bad-request helper"
    (is (= {:status 400 :body {:error "bad-request"}} (resp/bad-request nil)))
    (is (= {:status 400 :body {:message "error msg"}} (resp/bad-request "error msg")))
    (is (= {:status 400 :body {:custom "err"}} (resp/bad-request {:custom "err"}))))

  (testing "unauthorized helper"
    (is (= {:status 401 :body {:error "unauthorized"}} (resp/unauthorized nil)))
    (is (= {:status 401 :body {:message "unauthorized msg"}} (resp/unauthorized "unauthorized msg")))
    (is (= {:status 401 :body {:custom "err"}} (resp/unauthorized {:custom "err"}))))

  (testing "forbidden helper"
    (is (= {:status 403 :body {:error "forbidden"}} (resp/forbidden nil)))
    (is (= {:status 403 :body {:message "forbidden msg"}} (resp/forbidden "forbidden msg")))
    (is (= {:status 403 :body {:custom "err"}} (resp/forbidden {:custom "err"}))))

  (testing "too-many-requests helper"
    (is (= {:status 429 :body {:error "too-many-requests"}} (resp/too-many-requests nil)))
    (is (= {:status 429 :body {:message "limit msg"}} (resp/too-many-requests "limit msg")))
    (is (= {:status 429 :body {:custom "err"}} (resp/too-many-requests {:custom "err"}))))

  (testing "server-error helper"
    (is (= {:status 500 :body {:error "server-error"}} (resp/server-error nil)))
    (is (= {:status 500 :body {:custom "err"}} (resp/server-error {:custom "err"}))))

  (testing "not-found helper"
    (is (= {:status 404 :body {:error "not-found"}} (resp/not-found nil)))
    (is (= {:status 404 :body {:message "not found msg"}} (resp/not-found "not found msg")))
    (is (= {:status 404 :body {:custom "err"}} (resp/not-found {:custom "err"}))))

  (testing "no-content helper"
    (is (= {:status 204 :body nil} (resp/no-content))))

  (testing "deleted helper"
    (is (= {:status 200 :body {}} (resp/deleted)))
    (is (= {:status 200 :body {}} (resp/deleted "unused"))))

  (testing "updated helper"
    (is (= {:status 200 :body {}} (resp/updated nil)))
    (is (= {:status 200 :body {:id 1}} (resp/updated {:id 1})))
    (is (= {:status 200 :body {:result "some-string"}} (resp/updated "some-string"))))

  (testing "created helper"
    (is (= {:status 201 :body {}} (resp/created nil)))
    (is (= {:status 201 :body {:id 1}} (resp/created {:id 1})))
    (is (= {:status 201 :body {:result "some-string"}} (resp/created "some-string"))))

  (testing "file-response helper"
    (is (= {:status 200 :headers {"Content-Type" "image/png"} :body "dummy-file"}
           (resp/file-response "dummy-file" "image/png")))))

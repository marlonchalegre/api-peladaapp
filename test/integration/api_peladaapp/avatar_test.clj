(ns api-peladaapp.avatar-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is use-fixtures]]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(defn- create-test-image []
  (let [temp-file (java.io.File/createTempFile "test-avatar" ".png")]
    (with-open [out (io/output-stream temp-file)]
      (.write out (.getBytes "fake-image-content")))
    temp-file))

(deftest avatar-workflow-test
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
        token (th/register-and-login! app {:name "Avatar User" :email "avatar@example.com" :password "pass"})
        user-id (th/user-id-by-email ds "avatar@example.com")
        test-image (create-test-image)]

    ;; 1. Upload Avatar
    (let [resp (app (-> (mock/request :post (str "/api/user/" user-id "/avatar"))
                        (mock/header "Authorization" (str "Token " token))
                        (assoc :multipart-params {"avatar" {:tempfile test-image
                                                            :filename "test.png"
                                                            :content-type "image/png"
                                                            :size (.length test-image)}})))
          body (th/decode-body resp)]
      (is (= 200 (:status resp)))
      (is (some? (:avatar_filename body))))

    ;; 2. Serve Avatar (Authenticated)
    (let [resp (app (-> (mock/request :get (str "/api/user/" user-id "/avatar"))
                        (mock/header "Authorization" (str "Token " token))))]
      (is (= 200 (:status resp)))
      (is (= "image/png" (get-in resp [:headers "Content-Type"])))
      (is (= "private, max-age=3600" (get-in resp [:headers "Cache-Control"]))))

    ;; 3. Serve Avatar (Unauthenticated - Should fail)
    (let [resp (app (mock/request :get (str "/api/user/" user-id "/avatar")))]
      (is (= 401 (:status resp))))

    ;; 4. Delete Avatar
    (let [resp (app (-> (mock/request :delete (str "/api/user/" user-id "/avatar"))
                        (mock/header "Authorization" (str "Token " token))))]
      (is (= 204 (:status resp))))

    ;; 5. Verify it's gone
    (let [resp (app (-> (mock/request :get (str "/api/user/" user-id "/avatar"))
                        (mock/header "Authorization" (str "Token " token))))]
      (is (= 404 (:status resp))))))

(deftest avatar-upload-invalid-type
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
        token (th/register-and-login! app {:name "Avatar User" :email "avatar2@example.com" :password "pass"})
        user-id (th/user-id-by-email ds "avatar2@example.com")
        test-file (java.io.File/createTempFile "test" ".txt")]

    (with-open [out (io/output-stream test-file)]
      (.write out (.getBytes "not-an-image")))

    (let [resp (app (-> (mock/request :post (str "/api/user/" user-id "/avatar"))
                        (mock/header "Authorization" (str "Token " token))
                        (assoc :multipart-params {"avatar" {:tempfile test-file
                                                            :filename "test.txt"
                                                            :content-type "text/plain"
                                                            :size (.length test-file)}})))
          body (th/decode-body resp)]
      (is (= 400 (:status resp)))
      (is (clojure.string/includes? (:message body) "Invalid file type")))))

(deftest avatar-upload-too-large
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
        token (th/register-and-login! app {:name "Avatar User" :email "avatar3@example.com" :password "pass"})
        user-id (th/user-id-by-email ds "avatar3@example.com")
        test-file (java.io.File/createTempFile "large" ".png")]

    (let [resp (app (-> (mock/request :post (str "/api/user/" user-id "/avatar"))
                        (mock/header "Authorization" (str "Token " token))
                        (assoc :multipart-params {"avatar" {:tempfile test-file
                                                            :filename "large.png"
                                                            :content-type "image/png"
                                                            :size (* 3 1024 1024)}}))) ; 3MB
          body (th/decode-body resp)]
      (is (= 400 (:status resp)))
      (is (clojure.string/includes? (:message body) "File too large")))))

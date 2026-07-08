(ns api-peladaapp.handlers.avatar-test
  (:require
   [api-peladaapp.controllers.user :as controller.user]
   [api-peladaapp.handlers.avatar :as handler.avatar]
   [api-peladaapp.helpers.responses :as responses]
   [api-peladaapp.logic.authorization :as auth]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]))

(deftest test-upload-handler
  (let [db "dummy-db"
        user-id (random-uuid)]
    (testing "nil file-params returns 400"
      (with-redefs [auth/require-self-or-admin! (fn [_ _] true)]
        (let [resp (handler.avatar/upload {:params {:id (str user-id)} :database db})]
          (is (= 400 (:status resp)))
          (is (= "No file uploaded" (get-in resp [:body :message]))))))

    (testing "invalid content type returns 400"
      (with-redefs [auth/require-self-or-admin! (fn [_ _] true)]
        (let [resp (handler.avatar/upload {:params {:id (str user-id)}
                                           :database db
                                           :multipart-params {"avatar" {:content-type "text/plain" :size 100}}})]
          (is (= 400 (:status resp)))
          (is (str/includes? (get-in resp [:body :message]) "Invalid file type")))))

    (testing "file too large returns 400"
      (with-redefs [auth/require-self-or-admin! (fn [_ _] true)]
        (let [resp (handler.avatar/upload {:params {:id (str user-id)}
                                           :database db
                                           :multipart-params {"avatar" {:content-type "image/png" :size (* 3 1024 1024)}}})]
          (is (= 400 (:status resp)))
          (is (= "File too large. Max 2MB." (get-in resp [:body :message]))))))

    (testing "successful upload and old avatar cleanup"
      (let [copied (atom false)
            deleted-old (atom false)
            profile-updated (atom false)]
        (with-redefs [auth/require-self-or-admin! (fn [_ _] true)
                      controller.user/get-user (fn [id _]
                                                 (is (= user-id id))
                                                 {:avatar-filename "old-avatar.png"})
                      io/copy (fn [_ _] (reset! copied true))
                      controller.user/update-user-profile (fn [profile id _]
                                                            (is (= user-id id))
                                                            (is (some? (:avatar-filename profile)))
                                                            (reset! profile-updated true))
                      ;; Mock java.io.File to pretend the old avatar exists
                      io/file (fn [& args]
                                (let [filename (last args)]
                                  (proxy [java.io.File] [filename]
                                    (exists [] (= filename "old-avatar.png")))))
                      io/delete-file (fn [_ _] (reset! deleted-old true))]
          (let [resp (handler.avatar/upload {:params {:id (str user-id)}
                                             :database db
                                             :multipart-params {"avatar" {:content-type "image/png"
                                                                          :size 100
                                                                          :tempfile "tmp"}}})]
            (is (= 200 (:status resp)))
            (is @copied)
            (is @profile-updated)
            (is @deleted-old)))))))

(deftest test-serve-handler
  (let [db "dummy-db"
        user-id (random-uuid)]
    (testing "user has no avatar"
      (with-redefs [controller.user/get-user (fn [_ _] {})]
        (let [resp (handler.avatar/serve {:params {:id (str user-id)} :database db})]
          (is (= 404 (:status resp)))
          (is (= "User has no avatar" (get-in resp [:body :message]))))))

    (testing "avatar file does not exist on disk"
      (with-redefs [controller.user/get-user (fn [_ _] {:avatar-filename "missing.png"})
                    io/file (fn [& _] (proxy [java.io.File] ["missing.png"] (exists [] false)))]
        (let [resp (handler.avatar/serve {:params {:id (str user-id)} :database db})]
          (is (= 404 (:status resp)))
          (is (= "Avatar file not found" (get-in resp [:body :message]))))))

    (testing "avatar file exists webp content-type"
      (with-redefs [controller.user/get-user (fn [_ _] {:avatar-filename "avatar.webp"})
                    io/file (fn [& _] (proxy [java.io.File] ["avatar.webp"] (exists [] true)))
                    responses/file-response (fn [_ content-type]
                                              {:status 200
                                               :headers {"Content-Type" content-type}})]
        (let [resp (handler.avatar/serve {:params {:id (str user-id)} :database db})]
          (is (= 200 (:status resp)))
          (is (= "image/webp" (get-in resp [:headers "Content-Type"])))
          (is (= "private, max-age=3600" (get-in resp [:headers "Cache-Control"]))))))

    (testing "avatar file exists jpeg content-type"
      (with-redefs [controller.user/get-user (fn [_ _] {:avatar-filename "avatar.jpeg"})
                    io/file (fn [& _] (proxy [java.io.File] ["avatar.jpeg"] (exists [] true)))
                    responses/file-response (fn [_ content-type]
                                              {:status 200
                                               :headers {"Content-Type" content-type}})]
        (let [resp (handler.avatar/serve {:params {:id (str user-id)} :database db})]
          (is (= 200 (:status resp)))
          (is (= "image/jpeg" (get-in resp [:headers "Content-Type"]))))))))

(deftest test-delete-handler
  (let [db "dummy-db"
        user-id (random-uuid)]
    (testing "deletes avatar successfully"
      (let [deleted (atom false)
            profile-updated (atom false)]
        (with-redefs [auth/require-self-or-admin! (fn [_ _] true)
                      controller.user/get-user (fn [_ _] {:avatar-filename "avatar.png"})
                      io/file (fn [& _] (proxy [java.io.File] ["avatar.png"] (exists [] true)))
                      io/delete-file (fn [_ _] (reset! deleted true))
                      controller.user/update-user-profile (fn [profile id _]
                                                            (is (= user-id id))
                                                            (is (nil? (:avatar-filename profile)))
                                                            (reset! profile-updated true))]
          (let [resp (handler.avatar/delete {:params {:id (str user-id)} :database db})]
            (is (= 204 (:status resp)))
            (is @deleted)
            (is @profile-updated)))))))


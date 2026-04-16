(ns api-peladaapp.handlers.avatar
  (:require
   [api-peladaapp.controllers.user :as controller.user]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.responses :as responses]
   [api-peladaapp.logic.authorization :as auth]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def ^:private avatars-dir "uploads/avatars")

(defn ensure-avatars-dir! []
  (let [dir (io/file avatars-dir)]
    (when-not (.exists dir)
      (.mkdirs dir))))

;; Ensure dir on load
(ensure-avatars-dir!)

(def ^:private allowed-content-types
  #{"image/jpeg" "image/jpg" "image/png" "image/webp"})

(def ^:private extension-map
  {"image/jpeg" ".jpg"
   "image/jpg" ".jpg"
   "image/png" ".png"
   "image/webp" ".webp"})

(defn upload [request]
  (try
    (let [user-id (-> request :params :id parse-long)
          _ (auth/require-self-or-admin! request user-id)
          file-params (get-in request [:multipart-params "avatar"])
          temp-file (:tempfile file-params)
          content-type (:content-type file-params)
          size (:size file-params)
          db (:database request)]
      
      (cond
        (nil? file-params)
        (responses/bad-request "No file uploaded")

        (not (contains? allowed-content-types content-type))
        (responses/bad-request (str "Invalid file type. Allowed: " (str/join ", " allowed-content-types)))

        (> size (* 2 1024 1024)) ; 2MB limit
        (responses/bad-request "File too large. Max 2MB.")

        :else
        (let [ext (get extension-map content-type)
              filename (str "u" user-id "_" (System/currentTimeMillis) ext)
              dest-file (io/file avatars-dir filename)
              ;; Find existing avatar to delete later
              existing-user (controller.user/get-user user-id db)
              old-filename (:avatar-filename existing-user)]
          
          ;; Copy new file
          (io/copy temp-file dest-file)
          
          ;; Update DB
          (controller.user/update-user-profile {:avatar-filename filename} user-id db)
          
          ;; Delete old file if exists
          (when (and old-filename (not= old-filename filename))
            (let [old-file (io/file avatars-dir old-filename)]
              (when (.exists old-file)
                (io/delete-file old-file true))))
          
          (responses/ok {:avatar_filename filename}))))
    (catch Exception e
      (exception/api-exception-handler e))))

(defn serve [request]
  (try
    (let [user-id (-> request :params :id parse-long)
          db (:database request)
          user (controller.user/get-user user-id db)
          filename (:avatar-filename user)]
      
      (if (and filename (not (str/blank? filename)))
        (let [file (io/file avatars-dir filename)]
          (if (.exists file)
            (let [ext (last (str/split filename #"\."))
                  content-type (case ext
                                 "jpg" "image/jpeg"
                                 "jpeg" "image/jpeg"
                                 "png" "image/png"
                                 "webp" "image/webp"
                                 "application/octet-stream")]
              (-> (responses/file-response file content-type)
                  (assoc-in [:headers "Cache-Control"] "private, max-age=3600")))
            (responses/not-found "Avatar file not found")))
        (responses/not-found "User has no avatar")))
    (catch Exception e
      (exception/api-exception-handler e))))

(defn delete [request]
  (try
    (let [user-id (-> request :params :id parse-long)
          _ (auth/require-self-or-admin! request user-id)
          db (:database request)
          user (controller.user/get-user user-id db)
          filename (:avatar-filename user)]
      
      (when filename
        (let [file (io/file avatars-dir filename)]
          (when (.exists file)
            (io/delete-file file true)))
        (controller.user/update-user-profile {:avatar-filename nil} user-id db))
      
      (responses/no-content))
    (catch Exception e
      (exception/api-exception-handler e))))

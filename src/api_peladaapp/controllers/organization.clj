(ns api-peladaapp.controllers.organization
  (:require
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.helpers.pagination :as pagination]
   [api-peladaapp.models.organization :as models.organization]
   [schema.core :as s]))

(s/defn create-organization :- models.organization/Organization
  [org :- models.organization/Organization
   user-id :- (s/maybe s/Int)
   db]
  (let [id (db.organization/insert-organization org db)]
    ;; Add creator as admin (if user-id is provided)
    (when user-id
      (db.admin/insert-organization-admin {:organization_id id :user_id user-id} db))
    (db.organization/get-organization id db)))

(s/defn get-organization :- models.organization/Organization
  [id :- s/Int
   db]
  (let [org (db.organization/get-organization id db)]
    (if (nil? org)
      (throw (ex-info nil {:type :not-found :message "Organization not found"}))
      org)))

(s/defn update-organization :- models.organization/Organization
  [id :- s/Int
   org :- models.organization/Organization
   db]
  (let [rows (db.organization/update-organization id org db)]
    (if (zero? rows)
      (throw (ex-info nil {:type :not-found :message "Organization not found"}))
      (db.organization/get-organization id db))))

(s/defn delete-organization
  [id :- s/Int
   db]
  (let [rows (db.organization/delete-organization id db)]
    (if (zero? rows)
      (throw (ex-info nil {:type :not-found :message "Organization not found"}))
      rows)))

(s/defn list-organizations
  [db pagination]
  (let [page (or (:page pagination) 1)
        per-page (or (:per-page pagination) 20)
        offset (* (dec page) per-page)
        orgs   (db.organization/list-organizations db per-page offset)
        total  (db.organization/count-organizations db)]
    (pagination/with-pagination-headers orgs total page per-page)))

(s/defn get-statistics
  [id :- s/Int
   year :- s/Int
   db]
  (let [stats (db.organization/get-statistics id year db)]
    (reduce (fn [acc {:keys [event_type count]}]
              (assoc acc (keyword event_type) count))
            {:goal 0 :assist 0 :own_goal 0}
            stats)))
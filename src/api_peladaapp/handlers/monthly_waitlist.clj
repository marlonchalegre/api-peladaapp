(ns api-peladaapp.handlers.monthly-waitlist
  (:require
   [api-peladaapp.adapters.monthly-waitlist :as adapter.monthly-waitlist]
   [api-peladaapp.db.monthly-waitlist :as db.monthly-waitlist]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.responses :refer [created ok]]
   [api-peladaapp.logic.authorization :as auth]
   [api-peladaapp.logic.monthly-waitlist :as logic.monthly-waitlist]))

(defn list-waitlist [request]
  (try (let [db (:database request)
             org-id (misc/as-uuid (get-in request [:params :organization_id]))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id org-id db)
         (let [entries (db.monthly-waitlist/list-waitlist-by-org org-id db)]
           (ok (map adapter.monthly-waitlist/db->response entries))))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-my-status [request]
  (try (let [db (:database request)
             org-id (misc/as-uuid (get-in request [:params :organization_id]))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-member! user-id org-id db)
         (ok (adapter.monthly-waitlist/status->response
              (logic.monthly-waitlist/get-waitlist-status org-id user-id db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn add-candidate [request]
  (try (let [db (:database request)
             org-id (misc/as-uuid (get-in request [:params :organization_id]))
             user-id (auth/get-user-id-from-request request)
             body (:body request)
             body-player-id (some-> (:player_id body) misc/as-uuid)
             current-player (db.player/get-org-player-by-user-id user-id org-id db)
             target-player-id (or body-player-id (:id current-player))]
         (auth/require-organization-member! user-id org-id db)
         (when (and body-player-id (not= body-player-id (:id current-player)))
           (auth/require-organization-admin! user-id org-id db))
         (when-not target-player-id
           (throw (ex-info "Target player not found" {:type :bad-request :message "Target player not found"})))
         (let [res (logic.monthly-waitlist/add-candidate! org-id target-player-id db)]
           (created res)))
       (catch Exception e (exception/api-exception-handler e))))

(defn remove-candidate [request]
  (try (let [db (:database request)
             org-id (misc/as-uuid (get-in request [:params :organization_id]))
             player-id (misc/as-uuid (get-in request [:params :player_id]))
             user-id (auth/get-user-id-from-request request)
             is-admin? (auth/user-can-admin-organization? user-id org-id db)]
         (auth/require-organization-member! user-id org-id db)
         (ok (logic.monthly-waitlist/remove-candidate! org-id player-id user-id is-admin? db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn promote-candidate [request]
  (try (let [db (:database request)
             org-id (misc/as-uuid (get-in request [:params :organization_id]))
             player-id (misc/as-uuid (get-in request [:params :player_id]))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id org-id db)
         (ok (logic.monthly-waitlist/promote-candidate! org-id player-id db)))
       (catch Exception e (exception/api-exception-handler e))))

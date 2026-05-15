(ns api-peladaapp.controllers.manual-stats
  (:require
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.manual-stats :as db.manual-stats]
   [api-peladaapp.models.manual-stats :as models.manual-stats]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(s/defn upsert-manual-stats :- s/Int
  [requesting-user-id :- s/Uuid
   organization-id :- s/Uuid
   stats :- [models.manual-stats/ManualStats]
   db]
  (if (db.admin/is-user-admin-of-organization? requesting-user-id organization-id db)
    (jdbc/with-transaction [tx db]
      (doseq [stat stats]
        (db.manual-stats/upsert-manual-stats
         (assoc stat :organization-id organization-id)
         tx))
      (count stats))
    (throw (ex-info "Forbidden" {:type :forbidden :message "User is not an admin of this organization"}))))

(s/defn list-manual-stats :- [models.manual-stats/ManualStats]
  [organization-id :- s/Uuid
   year :- s/Int
   db]
  (db.manual-stats/list-manual-stats-by-org-and-year organization-id year db))

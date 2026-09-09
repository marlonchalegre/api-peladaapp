(ns api-peladaapp.adapters.monthly-waitlist
  (:require
   [medley.core :as medley.core]))

(defn db->response [row]
  (when row
    (medley.core/assoc-some {}
                            :id (:id row)
                            :organization_id (:organization_id row)
                            :player_id (:player_id row)
                            :user_id (:user_id row)
                            :user_name (:user_name row)
                            :user_username (:user_username row)
                            :user_avatar_filename (:user_avatar_filename row)
                            :position (:position row)
                            :member_type (:member_type row)
                            :created_at (some-> (:created_at row) str))))

(defn status->response [status]
  (medley.core/assoc-some {:in_queue (:in-queue status)}
                          :entry (:entry status)))

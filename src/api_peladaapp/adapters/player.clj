(ns api-peladaapp.adapters.player
  (:require [api-peladaapp.helpers.misc :as misc]
            [schema.core :as s]))

(defn in->model [{:keys [user_id organization_id grade position_id]}]
  (cond-> {}
    user_id (assoc :user_id user_id)
    organization_id (assoc :organization_id organization_id)
    (some? grade) (assoc :grade grade)
    (some? position_id) (assoc :position_id position_id)))

(s/defn model->out [p]
  (some-> p (select-keys [:id :user_id :organization_id :grade :position_id])))

(s/defn db->model [p]
  (some-> p misc/unamespace (select-keys [:id :user_id :organization_id :grade :position_id])))

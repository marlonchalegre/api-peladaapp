(ns api-100folego.adapters.vote
  (:require [api-100folego.helpers.misc :as misc]
            [schema.core :as s]))

(defn in->model [{:keys [pelada_id voter_id target_id stars]}]
  (cond-> {}
    pelada_id (assoc :pelada_id pelada_id)
    voter_id (assoc :voter_id voter_id)
    target_id (assoc :target_id target_id)
    stars (assoc :stars stars)))

(s/defn model->out [v]
  (some-> v (select-keys [:id :pelada_id :voter_id :target_id :stars :created_at])))

(s/defn db->model [v]
  (some-> v misc/unamespace (select-keys [:id :pelada_id :voter_id :target_id :stars :created_at])))

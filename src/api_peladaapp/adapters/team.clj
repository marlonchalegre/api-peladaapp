(ns api-peladaapp.adapters.team
  (:require [api-peladaapp.helpers.misc :as misc]
            [schema.core :as s]))

(defn in->model [{:keys [pelada_id name]}]
  (cond-> {}
    pelada_id (assoc :pelada_id pelada_id)
    name (assoc :name name)))

(s/defn model->out [team]
  (some-> team (select-keys [:id :pelada_id :name])))

(s/defn db->model [team]
  (some-> team misc/unamespace (select-keys [:id :pelada_id :name])))

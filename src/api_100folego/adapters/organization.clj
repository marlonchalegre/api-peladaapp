(ns api-100folego.adapters.organization
  (:require [api-100folego.helpers.misc :as misc]
            [schema.core :as s]))

(defn in->model [{:keys [name]}]
  (cond-> {}
    name (assoc :name name)))

(s/defn model->out [o]
  (some-> o (select-keys [:id :name])))

(s/defn db->model [o]
  (some-> o misc/unamespace (select-keys [:id :name])))

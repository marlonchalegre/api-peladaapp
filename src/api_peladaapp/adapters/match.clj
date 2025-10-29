(ns api-peladaapp.adapters.match
  (:require [api-peladaapp.helpers.misc :as misc]
            [schema.core :as s]))

(defn in->model [{:keys [pelada_id home_team_id away_team_id sequence status home_score away_score]}]
  (cond-> {}
    pelada_id (assoc :pelada_id pelada_id)
    home_team_id (assoc :home_team_id home_team_id)
    away_team_id (assoc :away_team_id away_team_id)
    sequence (assoc :sequence sequence)
    status (assoc :status status)
    (some? home_score) (assoc :home_score home_score)
    (some? away_score) (assoc :away_score away_score)))

(s/defn model->out [m]
  (some-> m (select-keys [:id :pelada_id :home_team_id :away_team_id :sequence :status :home_score :away_score])))

(s/defn db->model [m]
  (some-> m misc/unamespace (select-keys [:id :pelada_id :home_team_id :away_team_id :sequence :status :home_score :away_score])))

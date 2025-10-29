(ns api-100folego.adapters.pelada
  (:require [api-100folego.helpers.misc :as misc]
            [schema.core :as s]))

(defn in->model [{:keys [organization_id scheduled_at when num_teams players_per_team status]}]
  (cond-> {}
    organization_id (assoc :organization_id organization_id)
    (or scheduled_at when) (assoc :scheduled_at (or scheduled_at when))
    num_teams (assoc :num_teams num_teams)
    players_per_team (assoc :players_per_team players_per_team)
    status (assoc :status status)))

(s/defn model->out [pelada]
  (some-> pelada (select-keys [:id :organization_id :scheduled_at :num_teams :players_per_team :status])))

(s/defn db->model [pelada]
  (some-> pelada misc/unamespace (select-keys [:id :organization_id :scheduled_at :num_teams :players_per_team :status])))

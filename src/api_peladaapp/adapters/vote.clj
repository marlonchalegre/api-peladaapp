(ns api-peladaapp.adapters.vote
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.models.vote :as models.vote]
   [api-peladaapp.requests.vote :as requests.vote]
   [api-peladaapp.responses.vote :as responses.vote]
   [medley.core :as medley.core]
   [schema.core :as s]))

(s/defn create-request->model :- models.vote/Vote
  [request :- requests.vote/CastVoteRequest]
  {:pelada-id (:pelada_id request)
   :voter-id (:voter_id request)
   :target-id (:target_id request)
   :stars (:stars request)})

(s/defn model->response :- responses.vote/VoteResponse
  [model]
  {:id (:id model)
   :pelada_id (:pelada-id model)
   :voter_id (:voter-id model)
   :target_id (:target-id model)
   :stars (:stars model)
   :created_at (:created-at model)})

(s/defn batch-vote-model->response
  [model]
  {:votes_cast (:votes-cast model)})

(s/defn voting-info-model->response
  [model]
  (let [eligible-players (mapv (fn [p]
                                 (medley.core/assoc-some
                                  {:player_id (:player-id p)
                                   :name (:name p)
                                   :goals (:goals p)
                                   :assists (:assists p)
                                   :own_goals (:own-goals p)}
                                  :position (:position p)))
                               (:eligible-players model))
        current-votes (mapv model->response (:current-votes model))]
    (medley.core/assoc-some
     {:can_vote (:can-vote model)
      :has_voted (:has-voted model)
      :eligible_players eligible-players
      :current_votes current-votes}
     :voter_player_id (:voter-player-id model)
     :message (:message model))))

(s/defn voting-results-model->response
  [model]
  (let [map-player (fn [p]
                     (medley.core/assoc-some
                      {:player_id (:player-id p)
                       :name (:name p)
                       :average_stars (:average-stars p)
                       :goals (:goals p)
                       :assists (:assists p)
                       :own_goals (:own-goals p)}
                      :position (:position p)))]
    {:mvp (mapv map-player (:mvp model))
     :striker (mapv map-player (:striker model))
     :garcom (mapv map-player (:garcom model))
     :voters (mapv (fn [v]
                     {:player_id (:player-id v)
                      :name (:name v)
                      :has_voted (:has-voted v)})
                   (:voters model))
     :total_eligible (:total-eligible model)
     :total_voted (:total-voted model)}))

(s/defn voting-status-model->response
  [model]
  {:voters (mapv (fn [v]
                   {:player_id (:player-id v)
                    :name (:name v)
                    :has_voted (:has-voted v)})
                 (:voters model))
   :total_eligible (:total-eligible model)
   :total_voted (:total-voted model)})

(s/defn normalized-score-model->response
  [model]
  {:player_id (:player-id model)
   :score (:score model)})

(s/defn db->model :- models.vote/Vote
  [v]
  (when-let [p (some-> v misc/unamespace)]
    {:id (:id p)
     :pelada-id (:pelada_id p)
     :voter-id (:voter_id p)
     :target-id (:target_id p)
     :stars (:stars p)
     :created-at (:created_at p)}))
(ns api-peladaapp.adapters.vote
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.models.vote :as models.vote]
   [api-peladaapp.requests.vote :as requests.vote]
   [api-peladaapp.responses.vote :as responses.vote]
   [schema.core :as s]))

(s/defn create-request->model :- models.vote/Vote
  [request :- requests.vote/CastVoteRequest]
  {:pelada-id (:pelada_id request)
   :voter-id (:voter_id request)
   :target-id (:target_id request)
   :stars (:stars request)})

(s/defn model->response :- responses.vote/VoteResponse
  [{:keys [id pelada-id voter-id target-id stars created-at]}]
  {:id id
   :pelada_id pelada-id
   :voter_id voter-id
   :target_id target-id
   :stars stars
   :created_at created-at})

(s/defn db->model :- models.vote/Vote
  [v]
  (when-let [p (some-> v misc/unamespace)]
    {:id (:id p)
     :pelada-id (:pelada_id p)
     :voter-id (:voter_id p)
     :target-id (:target_id p)
     :stars (:stars p)
     :created-at (:created_at p)}))
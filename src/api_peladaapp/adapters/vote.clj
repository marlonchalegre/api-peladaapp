(ns api-peladaapp.adapters.vote
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.models.vote :as models.vote]
   [api-peladaapp.requests.vote :as requests.vote]
   [api-peladaapp.responses.vote :as responses.vote]
   [schema.core :as s]))

(s/defn create-request->model :- models.vote/Vote
  [request :- requests.vote/CastVoteRequest]
  (select-keys request [:pelada_id :voter_id :target_id :stars]))

(s/defn model->response :- responses.vote/VoteResponse
  [v :- models.vote/Vote]
  (select-keys v [:id :pelada_id :voter_id :target_id :stars :created_at]))

(s/defn db->model :- models.vote/Vote
  [v]
  (some-> v misc/unamespace (select-keys [:id :pelada_id :voter_id :target_id :stars :created_at])))
(ns api-peladaapp.requests.vote
  (:require
   [schema.core :as s]))

(s/defschema CastVoteRequest
  {:pelada_id s/Int
   :voter_id s/Int
   :target_id s/Int
   :stars s/Int})

(s/defschema VoteEntry
  {:target_id s/Int
   :stars s/Int})

(s/defschema BatchCastVoteRequest
  {:voter_id s/Int
   :votes [VoteEntry]})

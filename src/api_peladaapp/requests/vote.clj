(ns api-peladaapp.requests.vote
  (:require
   [schema.core :as s]))

(s/defschema CastVoteRequest
  {:pelada_id s/Uuid
   :voter_id s/Uuid
   :target_id s/Uuid
   :stars s/Int})

(s/defschema VoteEntry
  {:target_id s/Uuid
   :stars s/Int})

(s/defschema BatchCastVoteRequest
  {:voter_id s/Uuid
   :votes [VoteEntry]})

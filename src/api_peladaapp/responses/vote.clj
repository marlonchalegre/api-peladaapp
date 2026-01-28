(ns api-peladaapp.responses.vote
  (:require
   [schema.core :as s]))

(s/defschema VoteResponse
  {:id s/Int
   :pelada_id s/Int
   :voter_id s/Int
   :target_id s/Int
   :stars s/Int
   (s/optional-key :created_at) s/Any})

(s/defschema BatchVoteResponse
  {:votes_cast s/Int})

(s/defschema EligiblePlayer
  {:player_id s/Int
   :name s/Str})

(s/defschema VotingInfoResponse
  {:can_vote s/Bool
   :has_voted s/Bool
   :eligible_players [EligiblePlayer]
   (s/optional-key :voter_player_id) (s/maybe s/Int)
   (s/optional-key :message) s/Str})

(s/defschema NormalizedScoreResponse
  {:player_id s/Int
   :score s/Num})

(ns api-peladaapp.responses.vote
  (:require
   [schema.core :as s]))

(s/defschema VoteResponse
  {:id s/Int
   :pelada-id s/Int
   :voter-id s/Int
   :target-id s/Int
   :stars s/Int
   (s/optional-key :created-at) s/Any})

(s/defschema BatchVoteResponse
  {:votes-cast s/Int})

(s/defschema EligiblePlayer
  {:player-id s/Int
   :name s/Str
   (s/optional-key :goals) s/Int
   (s/optional-key :assists) s/Int
   (s/optional-key :own_goals) s/Int})

(s/defschema VotingInfoResponse
  {:can-vote s/Bool
   :has-voted s/Bool
   :eligible-players [EligiblePlayer]
   (s/optional-key :current-votes) [VoteResponse]
   (s/optional-key :voter-player-id) (s/maybe s/Int)
   (s/optional-key :message) s/Str})

(s/defschema NormalizedScoreResponse
  {:player-id s/Int
   :score s/Num})

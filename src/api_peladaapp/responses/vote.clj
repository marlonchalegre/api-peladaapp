(ns api-peladaapp.responses.vote
  (:require
   [schema.core :as s]))

(s/defschema VoteResponse
  {:id s/Uuid
   :pelada-id s/Uuid
   :voter-id s/Uuid
   :target-id s/Uuid
   :stars s/Int
   (s/optional-key :created-at) s/Any})

(s/defschema BatchVoteResponse
  {:votes-cast s/Int})

(s/defschema EligiblePlayer
  {:player-id s/Uuid
   :name s/Str
   (s/optional-key :position) (s/maybe s/Str)
   (s/optional-key :goals) s/Int
   (s/optional-key :assists) s/Int
   (s/optional-key :own-goals) s/Int
   (s/optional-key :voting-enabled) s/Bool})

(s/defschema VoterStatus
  {:player-id s/Uuid
   :name s/Str
   :has-voted s/Bool})

(s/defschema PlayerResult
  {:player-id s/Uuid
   :name s/Str
   :position (s/maybe s/Str)
   :average-stars s/Num
   :goals s/Int
   :assists s/Int
   :own-goals s/Int})

(s/defschema VotingResultsResponse
  {:mvp [PlayerResult]
   :striker [PlayerResult]
   :garcom [PlayerResult]
   :voters [VoterStatus]
   :total-eligible s/Int
   :total-voted s/Int
   (s/optional-key :organization-id) s/Uuid
   (s/optional-key :organization-name) s/Str})

(s/defschema VotingStatusResponse
  {:voters [VoterStatus]
   :total-eligible s/Int
   :total-voted s/Int})

(s/defschema VotingInfoResponse
  {:can-vote s/Bool
   :has-voted s/Bool
   :eligible-players [EligiblePlayer]
   (s/optional-key :current-votes) [VoteResponse]
   (s/optional-key :voter-player-id) (s/maybe s/Uuid)
   (s/optional-key :message) s/Str})

(ns api-peladaapp.models.finance
  (:require
   [schema.core :as s]))

(s/defschema OrganizationFinance
  {:id s/Int
   :organization-id s/Int
   :mensalista-price s/Num
   :diarista-price s/Num
   :currency s/Str})

(s/defschema Transaction
  {:id s/Int
   :organization-id s/Int
   (s/optional-key :player-id) (s/maybe s/Int)
   (s/optional-key :pelada-id) (s/maybe s/Int)
   :amount s/Num
   :type (s/enum "income" "expense")
   :category s/Str
   (s/optional-key :description) (s/maybe s/Str)
   :payment-date s/Str
   (s/optional-key :created-by) (s/maybe s/Int)
   (s/optional-key :status) (s/enum "paid" "reversed")
   :created-at s/Str})

(s/defschema MonthlyPayment
  {:id s/Int
   :organization-id s/Int
   :player-id s/Int
   :year s/Int
   :month s/Int
   (s/optional-key :transaction-id) (s/maybe s/Int)
   :paid s/Bool})

(s/defschema FinanceSummary
  {:total-balance s/Num
   :total-income s/Num
   :total-expense s/Num
   :monthly-expected s/Num
   :monthly-received s/Num})

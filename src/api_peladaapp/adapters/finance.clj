(ns api-peladaapp.adapters.finance
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [medley.core :as medley.core]))

(defn- extract-id [row]
  (or (:id row)
      (get row (keyword "last_insert_rowid()"))
      (get row "last_insert_rowid()")))

;; --- Organization Finance ---

(defn db->finance [row]
  (when row
    (let [row (misc/unamespace row)]
      {:id (extract-id row)
       :organization-id (:organization_id row)
       :mensalista-price (:mensalista_price row)
       :diarista-price (:diarista_price row)
       :monthly-fine-amount (:monthly_fine_amount row)
       :monthly-cut-off-day (:monthly_cut_off_day row)
       :currency (:currency row)})))

(defn finance->db [model]
  (when model
    (medley.core/assoc-some {}
                            :organization_id (:organization-id model)
                            :mensalista_price (:mensalista-price model)
                            :diarista_price (:diarista-price model)
                            :monthly_fine_amount (:monthly-fine-amount model)
                            :monthly_cut_off_day (:monthly-cut-off-day model)
                            :currency (:currency model))))

(defn payload->finance [payload]
  (when payload
    (let [p (misc/unamespace payload)]
      (medley.core/assoc-some {}
                              :id (:id p)
                              :organization-id (:organization_id p)
                              :mensalista-price (:mensalista_price p)
                              :diarista-price (:diarista_price p)
                              :monthly-fine-amount (:monthly_fine_amount p)
                              :monthly-cut-off-day (:monthly_cut_off_day p)
                              :currency (:currency p)))))

(defn model->finance-response [model]
  (when model
    {:id (:id model)
     :organization_id (:organization-id model)
     :mensalista_price (:mensalista-price model)
     :diarista_price (:diarista-price model)
     :monthly_fine_amount (:monthly-fine-amount model)
     :monthly_cut_off_day (:monthly-cut-off-day model)
     :currency (:currency model)}))

;; --- Transactions ---

(defn db->transaction [row]
  (when row
    (let [row (misc/unamespace row)]
      (medley.core/assoc-some {}
                              :id (extract-id row)
                              :organization-id (:organization_id row)
                              :player-id (:player_id row)
                              :player-name (:player_name row)
                              :pelada-id (:pelada_id row)
                              :amount (:amount row)
                              :fine-amount (:fine_amount row)
                              :type (:type row)
                              :category (:category row)
                              :description (:description row)
                              :status (:status row)
                              :payment-date (:payment_date row)
                              :created-by (:created_by row)
                              :creator-name (:creator_name row)
                              :created-at (:created_at row)))))

(defn transaction->db [model]
  (when model
    (medley.core/assoc-some {}
                            :organization_id (:organization-id model)
                            :player_id (:player-id model)
                            :pelada_id (:pelada-id model)
                            :amount (:amount model)
                            :fine_amount (:fine-amount model)
                            :type (:type model)
                            :category (:category model)
                            :description (:description model)
                            :status (or (:status model) "paid")
                            :payment_date (:payment-date model)
                            :created_by (:created-by model))))

(defn payload->transaction [payload]
  (when payload
    (let [p (misc/unamespace payload)]
      (medley.core/assoc-some {}
                              :id (:id p)
                              :organization-id (:organization_id p)
                              :player-id (:player_id p)
                              :pelada-id (:pelada_id p)
                              :amount (:amount p)
                              :fine-amount (:fine_amount p)
                              :type (:type p)
                              :category (:category p)
                              :description (:description p)
                              :status (:status p)
                              :payment-date (:payment_date p)
                              :created-by (:created_by p)))))

(defn model->transaction-response [model]
  (when model
    (medley.core/assoc-some {}
                            :id (:id model)
                            :organization_id (:organization-id model)
                            :player_id (:player-id model)
                            :player_name (:player-name model)
                            :pelada_id (:pelada-id model)
                            :amount (:amount model)
                            :fine_amount (:fine-amount model)
                            :type (:type model)
                            :category (:category model)
                            :description (:description model)
                            :status (:status model)
                            :payment_date (:payment-date model)
                            :created_by (:created-by model)
                            :creator_name (:creator-name model)
                            :created_at (:created-at model))))

;; --- Monthly Payments ---

(defn db->monthly-payment [row]
  (when row
    (let [row (misc/unamespace row)]
      (medley.core/assoc-some {}
                              :id (extract-id row)
                              :organization-id (:organization_id row)
                              :player-id (:player_id row)
                              :player-name (:player_name row)
                              :year (:year row)
                              :month (:month row)
                              :transaction-id (:transaction_id row)
                              :fine-transaction-id (:fine_transaction_id row)
                              :amount (:amount row)
                              :fine-amount (or (:actual_fine_amount row) (:fine_amount row))
                              :fine-status (:fine_status row)
                              :paid (if (contains? row :paid)
                                      (if (number? (:paid row))
                                        (not (zero? (:paid row)))
                                        (boolean (:paid row)))
                                      nil)))))

(defn monthly-payment->db [model]
  (when model
    (medley.core/assoc-some {}
                            :organization_id (:organization-id model)
                            :player_id (:player-id model)
                            :year (:year model)
                            :month (:month model)
                            :transaction_id (:transaction-id model)
                            :fine_transaction_id (:fine-transaction-id model)
                            :paid (if (contains? model :paid)
                                    (if (:paid model) 1 0)
                                    nil))))

(defn payload->monthly-payment [payload]
  (when payload
    (let [p (misc/unamespace payload)]
      (medley.core/assoc-some {}
                              :id (:id p)
                              :organization-id (:organization_id p)
                              :player-id (:player_id p)
                              :year (:year p)
                              :month (:month p)
                              :transaction-id (:transaction_id p)
                              :fine-transaction-id (:fine_transaction_id p)
                              :paid (:paid p)))))

(defn model->monthly-payment-response [model]
  (when model
    (medley.core/assoc-some {}
                            :id (:id model)
                            :organization_id (:organization-id model)
                            :player_id (:player-id model)
                            :player_name (:player-name model)
                            :year (:year model)
                            :month (:month model)
                            :transaction_id (:transaction-id model)
                            :fine_transaction_id (:fine-transaction-id model)
                            :amount (:amount model)
                            :fine_amount (:fine-amount model)
                            :fine_status (:fine-status model)
                            :paid (:paid model))))

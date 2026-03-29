(ns api-peladaapp.adapters.finance
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [medley.core :as medley.core]))

(defn- extract-id [row]
  (or (:id row)
      (get row (keyword "last_insert_rowid()"))
      (get row "last_insert_rowid()")))

(defn db->finance [row]
  (when row
    (let [row (misc/unamespace row)]
      {:id (extract-id row)
       :organization-id (or (:organization_id row) (:organization-id row))
       :mensalista-price (or (:mensalista_price row) (:mensalista-price row))
       :diarista-price (or (:diarista_price row) (:diarista-price row))
       :currency (:currency row)})))

(defn finance->db [model]
  (when model
    (medley.core/assoc-some {}
                            :organization_id (or (:organization-id model) (:organization_id model))
                            :mensalista_price (or (:mensalista-price model) (:mensalista-price model))
                            :diarista_price (or (:diarista-price model) (:diarista-price model))
                            :currency (:currency model))))

(defn db->transaction [row]
  (when row
    (let [row-un (misc/unamespace row)]
      (medley.core/assoc-some {}
                              :id (extract-id row-un)
                              :organization-id (or (:organization_id row-un) (:organization-id row-un))
                              :player-id (or (:player_id row-un) (:player-id row-un))
                              :player-name (or (:player_name row-un) (:player-name row-un))
                              :pelada-id (or (:pelada_id row-un) (:pelada-id row-un))
                              :amount (:amount row-un)
                              :type (:type row-un)
                              :category (:category row-un)
                              :description (:description row-un)
                              :status (or (:status row-un) "paid")
                              :payment-date (or (:payment_date row-un) (:payment-date row-un))
                              :created-by (or (:created_by row-un) (:created-by row-un))
                              :creator-name (or (:creator_name row-un) (:creator-name row-un))
                              :created-at (or (:created_at row-un) (:created-at row-un))))))

(defn transaction->db [model]
  (when model
    (medley.core/assoc-some {}
                            :organization_id (or (:organization-id model) (:organization_id model))
                            :player_id (or (:player-id model) (:player_id model))
                            :pelada_id (or (:pelada-id model) (:pelada_id model))
                            :amount (:amount model)
                            :type (:type model)
                            :category (:category model)
                            :description (:description model)
                            :status (or (:status model) "paid")
                            :payment_date (or (:payment-date model) (:payment_date model))
                            :created_by (or (:created-by model) (:created-by model)))))

(defn db->monthly-payment [row]
  (when row
    (let [row (misc/unamespace row)]
      (medley.core/assoc-some {}
                              :id (extract-id row)
                              :organization-id (or (:organization_id row) (:organization-id row))
                              :player-id (or (:player_id row) (:player-id row))
                              :player-name (or (:player_name row) (:player-name row))
                              :year (:year row)
                              :month (:month row)
                              :transaction-id (or (:transaction_id row) (:transaction-id row))
                              :paid (if (contains? row :paid)
                                      (if (number? (:paid row))
                                        (not (zero? (:paid row)))
                                        (boolean (:paid row)))
                                      nil)))))

(defn monthly-payment->db [model]
  (when model
    (medley.core/assoc-some {}
                            :organization_id (or (:organization-id model) (:organization_id model))
                            :player_id (or (:player-id model) (:player_id model))
                            :year (:year model)
                            :month (:month model)
                            :transaction_id (or (:transaction-id model) (:transaction_id model))
                            :paid (if (contains? model :paid)
                                    (if (:paid model) 1 0)
                                    nil))))

(defn model->finance-response [model]
  (when model
    {:id (:id model)
     :organization_id (:organization-id model)
     :mensalista_price (:mensalista-price model)
     :diarista_price (:diarista-price model)
     :currency (:currency model)}))

(defn model->transaction-response [model]
  (when model
    (medley.core/assoc-some {}
                            :id (:id model)
                            :organization_id (:organization-id model)
                            :player_id (:player-id model)
                            :player_name (:player-name model)
                            :pelada_id (:pelada-id model)
                            :amount (:amount model)
                            :type (:type model)
                            :category (:category model)
                            :description (:description model)
                            :status (:status model)
                            :payment_date (:payment-date model)
                            :created_by (:created-by model)
                            :creator_name (:creator-name model)
                            :created_at (:created-at model))))

(defn model->monthly-payment-response [model]
  (when model
    (medley.core/assoc-some {}
                            :id (:id model)
                            :organization_id (:organization-id model)
                            :player_id (:player-id model)
                            :player_name (:player-name model)
                            :year (:year model)
                            :month (:month model)
                            :transaction-id (:transaction-id model)
                            :paid (:paid model))))

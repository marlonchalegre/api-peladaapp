(ns api-peladaapp.adapters.organization
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.models.organization :as models.organization]
   [api-peladaapp.requests.organization :as requests.organization]
   [api-peladaapp.responses.organization :as responses.organization]
   [clojure.string :as str]
   [medley.core :as medley.core]
   [schema.core :as s]))

(s/defn create-request->model :- models.organization/Organization
  [request :- requests.organization/CreateOrganizationRequest]
  (-> request
      (select-keys [:name :waha_api_url :waha_instance :waha_group_id :waha_enabled :waha_start_msg_enabled :waha_end_msg_enabled :waha_attendance_reminder_enabled :waha_vote_reminder_enabled :waha_vote_ended_msg_enabled :waha_use_all_mention])
      (update-keys (comp keyword #(str/replace % "_" "-") name))))

(s/defn update-request->model :- models.organization/Organization
  [request :- requests.organization/UpdateOrganizationRequest]
  (-> request
      (select-keys [:name :waha_api_url :waha_instance :waha_group_id :waha_enabled :waha_start_msg_enabled :waha_end_msg_enabled :waha_attendance_reminder_enabled :waha_vote_reminder_enabled :waha_vote_ended_msg_enabled :waha_use_all_mention])
      (update-keys (comp keyword #(str/replace % "_" "-") name))))

(s/defn model->response :- responses.organization/OrganizationResponse
  [model :- models.organization/Organization]
  (-> model
      (select-keys [:id :name :owner-id :role :waha-api-url :waha-instance :waha-group-id :waha-enabled :waha-start-msg-enabled :waha-end-msg-enabled :waha-attendance-reminder-enabled :waha-vote-reminder-enabled :waha-vote-ended-msg-enabled :waha-use-all-mention :is-blocked])
      (update-keys (comp keyword #(str/replace % "-" "_") name))))

(s/defn db->model :- models.organization/Organization
  [o]
  (some-> o
          misc/unamespace
          (select-keys [:id :name :role :owner_id :waha_api_url :waha_instance :waha_group_id :waha_enabled :waha_start_msg_enabled :waha_end_msg_enabled :waha_attendance_reminder_enabled :waha_vote_reminder_enabled :waha_vote_ended_msg_enabled :waha_use_all_mention :is_blocked])
          (update-keys (comp keyword #(str/replace % "_" "-") name))
          (update :waha-enabled #(if (boolean? %) % (= 1 %)))
          (update :waha-start-msg-enabled #(if (boolean? %) % (= 1 %)))
          (update :waha-end-msg-enabled #(if (boolean? %) % (= 1 %)))
          (update :waha-attendance-reminder-enabled #(if (boolean? %) % (= 1 %)))
          (update :waha-vote-reminder-enabled #(if (boolean? %) % (= 1 %)))
          (update :waha-vote-ended-msg-enabled #(if (boolean? %) % (= 1 %)))
          (update :waha-use-all-mention #(if (nil? %) true (if (boolean? %) % (= 1 %))))
          (update :is-blocked #(if (nil? %) false %))))
(s/defn model->db [model :- models.organization/Organization]
  (-> model
      (select-keys [:id :name :owner-id])
      (update-keys (comp keyword #(str/replace % "-" "_") name))))

(defn accept-invitation-response->frontend [result]
  {:organization_id (:organization-id result)})

(defn invite-player-response->frontend [result]
  {:user_id (:user-id result)
   :player_id (:player-id result)
   :email (:email result)
   :name (:name result)
   :token (:token result)
   :is_new_user (:is-new-user result)
   :organization_id (:organization-id result)})

   ;; --- Monthly Player Substitutions ---

(defn db->substitution [row]
  (when row
    (let [row (misc/unamespace row)]
      (medley.core/assoc-some {}
                              :id (:id row)
                              :organization-id (:organization_id row)
                              :permanent-player-id (:permanent_player_id row)
                              :permanent-player-name (:permanent_player_name row)
                              :temporary-player-id (:temporary_player_id row)
                              :temporary-player-name (:temporary_player_name row)
                              :start-date (some-> (:start_date row) str)
                              :end-date (some-> (:end_date row) str)
                              :active (if (contains? row :active)
                                        (if (number? (:active row))
                                          (not (zero? (:active row)))
                                          (boolean (:active row)))
                                        nil)
                              :created-at (:created_at row)))))

(defn model->substitution-response [model]
  (when model
    (medley.core/assoc-some {}
                            :id (:id model)
                            :organization_id (:organization-id model)
                            :permanent_player_id (:permanent-player-id model)
                            :permanent_player_name (:permanent-player-name model)
                            :temporary_player_id (:temporary-player-id model)
                            :temporary_player_name (:temporary-player-name model)
                            :start_date (:start-date model)
                            :end_date (:end-date model)
                            :active (:active model)
                            :created_at (:created-at model))))
(ns api-peladaapp.adapters.organization
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.models.organization :as models.organization]
   [api-peladaapp.requests.organization :as requests.organization]
   [api-peladaapp.responses.organization :as responses.organization]
   [clojure.string :as str]
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
      (select-keys [:id :name :owner-id :waha-api-url :waha-instance :waha-group-id :waha-enabled :waha-start-msg-enabled :waha-end-msg-enabled :waha-attendance-reminder-enabled :waha-vote-reminder-enabled :waha-vote-ended-msg-enabled :waha-use-all-mention])
      (update-keys (comp keyword #(str/replace % "-" "_") name))))

(s/defn db->model :- models.organization/Organization
  [o]
  (some-> o
          misc/unamespace
          (select-keys [:id :name :owner_id :waha_api_url :waha_instance :waha_group_id :waha_enabled :waha_start_msg_enabled :waha_end_msg_enabled :waha_attendance_reminder_enabled :waha_vote_reminder_enabled :waha_vote_ended_msg_enabled :waha_use_all_mention])
          (update-keys (comp keyword #(str/replace % "_" "-") name))
          (update :waha-enabled #(= 1 %))
          (update :waha-start-msg-enabled #(= 1 %))
          (update :waha-end-msg-enabled #(= 1 %))
          (update :waha-attendance-reminder-enabled #(= 1 %))
          (update :waha-vote-reminder-enabled #(= 1 %))
          (update :waha-vote-ended-msg-enabled #(= 1 %))
          (update :waha-use-all-mention #(if (nil? %) true (= 1 %)))))
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
   :is_new_user (:is-new-user result)
   :organization_id (:organization-id result)})
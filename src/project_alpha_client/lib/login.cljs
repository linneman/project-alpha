;;;
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; functions for login pane (login.html)
;;;
;;; 2011-11-23, Otto Linnemann

(ns project-alpha-client.lib.login
  (:require [clojure.browser.dom :as dom]
            [goog.events :as events]
            [goog.style :as style]
            [project-alpha-client.lib.json :as json]
            [project-alpha-client.lib.dispatch :as dispatch])
  (:use [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.utils :only [send-request
                                               get-modal-dialog
                                               open-modal-dialog]]))

;;; the login pane
(def login-pane (dom/get-element "login-pane"))

(when login-pane

  ;; instantiate login dialog and confirmation button
  (let [[dialog ok-button cancel-button]
        (get-modal-dialog
         :panel-id "login"
         :title-id "login-dialog-title"
         :ok-button-id "confirm-login"
         :dispatched-event :login-dialog-confirmed)]
    (def login-dialog dialog)
    (def confirm-login-button ok-button))


  (let [[dialog ok-button cancel-button]
        (get-modal-dialog
         :panel-id "pw-forgotten-form"
         :title-id "pw-forgotten-dialog-title"
         :ok-button-id "confirm-pw-forgotten"
         :dispatched-event :pw-forotten-dialog-confirmed
         :keep-open true)]
    (def pw-forgotten-dialog dialog)
    (def confirm-pw-forgotten-button ok-button)
    (def progress_pane (dom/get-element "send_pw_reminder_progress"))
    (style/showElement progress_pane false)
    (style/setOpacity progress_pane 1))


  ;; instantiate login failed dialog
  (let [[dialog ok-button cancel-button]
        (get-modal-dialog
         :panel-id "login-failed"
         :title-id "login-failed-dialog-title"
         :ok-button-id "confirm-login-failed")]
    (def login-failed-dialog dialog)
    (def confirm-login-button ok-button))


  ;; instantiate login user not confirmed dialog
  (let [[dialog ok-button cancel-button]
        (get-modal-dialog
         :panel-id "login-user-not-confirmed"
         :title-id "login-user-not-confirmed-title"
         :ok-button-id "confirm-login-user-not-confirmed")]
    (def login-user-not-confirmed dialog)
    (def login-not-confirmed-button ok-button))

  ;; instantiate wrong account dialog
  (let [[dialog ok-button cancel-button]
        (get-modal-dialog
         :panel-id "login-wrong-account"
         :title-id "login-wrong-account-title"
         :ok-button-id "confirm-wrong-account-confirmed")]
    (def login-wrong-account dialog)
    (def login-wrong-account-button ok-button))

  ;; instantiate dialog which instructs the user to check the email
  ;; password reset
  (let [[dialog ok-button cancel-button]
        (get-modal-dialog
         :panel-id "reset_pw_advice_dialog"
         :title-id "reset_pw_advice_title"
         :ok-button-id "reset_pw_advice_button")]
    (def reset-pw-advice-dialog dialog))


  ;; instantiate error dialog about user for pw reset does not exist
  (let [[dialog ok-button cancel-button]
        (get-modal-dialog
         :panel-id "user_not_existing_dialog"
         :title-id "user_not_existing_title"
         :ok-button-id "user_not_existing_button")]
    (def user_not_existing_dialog dialog))

  )   ; (when login-pane




(defn- open-login-failed-dialog
  "opens the login failed dialog"
  []
  (open-modal-dialog login-failed-dialog))


(defn- open-login-user-not-confirmed-dialog
  "opens the user is not confirmed dialog"
  []
  (open-modal-dialog login-user-not-confirmed))


(defn- open-login-user-wrong-account-dialog
  "opens the user is not confirmed dialog"
  []
  (open-modal-dialog login-wrong-account))


(defn- open-reset-pw-advice-dialog
  "opens the reset password process advice dialog"
  []
  (open-modal-dialog reset-pw-advice-dialog))


(defn- open-user_not_existing_dialog
  "opens the user unknown after password reset req dialog"
  []
  (open-modal-dialog user_not_existing_dialog))


;;; clojurescript based event processing

(def login-confirm-reactor
  (dispatch/react-to
   #{:login-dialog-confirmed}
   (fn [evt data]
     (let [name (. (dom/get-element "login-name") -value)
           password (. (dom/get-element "login-password") -value)]
       (send-request "/login"
                     (json/generate {"name" name "password" password})
                     (fn [ajax-evt]
                       (let [resp (. (. ajax-evt -target) (getResponseText))]
                         (dispatch/fire :login-resp
                                        {:name name :resp resp})))
                     "POST")))))

(def login-resp-reactor
  (dispatch/react-to
   #{:login-resp}
   (fn [evt data]
     (let [{:keys [name resp]} data]
       (condp = resp
         "OK" (dispatch/fire :changed-login-state {:state :login :name name})
         "NOT CONFIRMED" (open-login-user-not-confirmed-dialog)
         "WRONG ACCOUNT" (open-login-user-wrong-account-dialog)
         (open-login-failed-dialog))))))


;;; exports

(defn open-login-dialog
  "opens the login dialog"
  []
  (open-modal-dialog login-dialog))


(defn open-pw-forgotten-dialog
  "opens the dialog to send a password reset request via email"
  []
  (open-modal-dialog pw-forgotten-dialog))


(defn send-logout-request
  "sends logout indication to server and change
   to logout state when positive response arrives."
  []
  (send-request "/logout" ""
                (fn [e] (let [xhr (. e -target)
                              resp (. xhr (getResponseText))]
                          (if (= resp "OK")
                            (dispatch/fire :changed-login-state
                                           {:state :logout}))))
                "POST"))


(defn set-progress-pane-visible
  "crossfades progress message over content of
   pw reminder dialog indicating the wating
   for server response."
  [visible]
  (style/showElement progress_pane visible))


(def pw-forgotten-confirm-reactor
  (dispatch/react-to
   #{:pw-forotten-dialog-confirmed}
   (fn [evt data]
     (let [name (. (dom/get-element "pw-reminder-name") -value)]
       (set-progress-pane-visible true)
       (send-request "/reset_pw_req"
                     (json/generate {"name" name})
                     (fn [ajax-evt]
                       (let [resp (. (. ajax-evt -target) (getResponseText))]
                         (dispatch/fire :pw-forgotten-resp
                                        {:name name :resp resp})))
                     "POST")))))


(def pw-forgotten-resp-reactor
  (dispatch/react-to
   #{:pw-forgotten-resp}
   (fn [evt data]
     (let [{:keys [name resp]} data]
       (set-progress-pane-visible false)
       (. pw-forgotten-dialog (setVisible false))
       (condp = resp
         "OK" (do (dispatch/fire :changed-login-state {:state :registered})
                  (open-reset-pw-advice-dialog))
         (open-user_not_existing_dialog))))))

;;;
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; functions for register pane (register.html)
;;;
;;; 2011-11-23, Otto Linnemann

(ns project-alpha-client.lib.register
  (:require [project-alpha-client.lib.json :as json]
            [project-alpha-client.lib.editor :as editor]
            [project-alpha-client.lib.dispatch :as dispatch]
            [clojure.browser.event :as event]
            [clojure.browser.dom   :as dom]
            [goog.net.XhrIo :as ajax]
            [goog.net.XhrManager :as XhrManager]
            [goog.style :as style]
            [goog.events :as events]
            [goog.object :as object]
            [goog.ui.Component :as Component]
            [goog.Timer :as timer])
  (:use [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.utils :only
         [send-request validate-email copy-id-text clear-id-text
          get-modal-dialog open-modal-dialog]]))

;; the register pane
(def register-pane (dom/get-element "register-pane"))

(when register-pane
  ;; instantiate registration dialog and confirmation button
  (let [[dialog ok-button cancel-button]
        (get-modal-dialog
         :panel-id "register"
         :title-id "register-dialog-title"
         :ok-button-id "confirm-registration"
         :cancel-button-id "cancel-registration"
         :dispatched-event :registration-dialog-confirmed)]
    (. ok-button (setEnabled false)) ; ok-button is only enalbed when form correct
    (def dialog dialog)
    (def confirm-button ok-button))


  ;; instantiate dialog which instructs the user to check the email for confirmation
  (let [[dialog ok-button cancel-button]
        (get-modal-dialog
         :panel-id "register-confirm-advice"
         :title-id "register-confirm-advice-title"
         :ok-button-id "register-confirm-advice-button")]
    (def register-confirm-advice-dialog dialog))


  ;; instantiate communication error dialog
  (let [[dialog ok-button cancel-button]
        (get-modal-dialog
         :panel-id "register-com-error"
         :title-id "register-com-error-title"
         :ok-button-id "register-com-button")]
    (def register-com-error-dialog dialog))

  (defn- when-user-exists
    "function is executed when user does exist
       with user data as argument."
    [name function]
    (send-request (str "/user/" (goog.string.urlEncode name)) nil
                  (fn [e] (let [text (. (.target e) (getResponseText))
                                data (json/parse text)]
                            (if (not-empty data) (function data))))))

  (comment
    usage illustration

    (when-user-exists "Otto" #(loginfo (pr-str "User exists, data: " %)))
    (when-user-exists "linneman@gmx.de" #(loginfo (pr-str "User exists, data: " %)))
    (when-user-exists "Otto" #(loginfo (% "name")))
    (when-user-exists "Otto2" (fn [data] (loginfo (data "name"))))
    )


  (def reg-form-status (atom
                        {:name "undefined"
                         :email "undefined"
                         :password "undefined"
                         :password-repeat "undefined"}))

  (defn- set-name-error
    "set an error message with respect to name field"
    [dom-error-id-string]
    (swap! reg-form-status assoc :name dom-error-id-string)
    (copy-id-text dom-error-id-string "register_message_name")
    (set! (.color (.style (dom/get-element "name"))) "red")
    )

  (defn- clear-name-error
    "remove all error message with respect to name field"
    []
    (swap! reg-form-status dissoc :name)
    (clear-id-text "register_message_name")
    (set! (.color (.style (dom/get-element "name"))) "green")
    )

  (defn- set-email-error
    "set an error message with respect to email field"
    [dom-error-id-string]
    (swap! reg-form-status assoc :email dom-error-id-string)
    (copy-id-text dom-error-id-string "register_message_email")
    (set! (.color (.style (dom/get-element "email"))) "red")
    )

  (defn- clear-email-error
    "remove all error message with respect to email field"
    []
    (swap! reg-form-status dissoc :email)
    (clear-id-text "register_message_email")
    (set! (.color (.style (dom/get-element "email"))) "green")
    )

  (defn- set-password-error
    "set an error message with respect to password field"
    [dom-error-id-string]
    (swap! reg-form-status assoc :password dom-error-id-string)
    (copy-id-text dom-error-id-string "register_message_password")
    (set! (.color (.style (dom/get-element "password"))) "red")
    )

  (defn- clear-password-error
    "remove all error message with respect to password field"
    []
    (swap! reg-form-status dissoc :password)
    (clear-id-text "register_message_password")
    (set! (.color (.style (dom/get-element "password"))) "green")
    )

  (defn- set-password-repeat-error
    "set an error message with respect to password repeat field"
    [dom-error-id-string]
    (swap! reg-form-status assoc :password-repeat dom-error-id-string)
    (copy-id-text dom-error-id-string "register_message_password_repeat")
    (set! (.color (.style (dom/get-element "password-repeat"))) "red")
    )

  (defn- clear-password-repeat-error
    "remove all error message with respect to password repeat field"
    []
    (swap! reg-form-status dissoc :password-repeat)
    (clear-id-text "register_message_password_repeat")
    (set! (.color (.style (dom/get-element "password-repeat"))) "green")
    )

  (defn- update-confirm-button-state []
    (if (empty? @reg-form-status)
      (do (. confirm-button (setEnabled true)) true)
      (do (. confirm-button (setEnabled false)) false)))


  (defn- updateRegisterText
    "validates registration form"
    [e]
    (let [target (.target e)
          target-id (.id target)
          target-elem (dom/get-element target-id)
          value (.value target-elem)]
      (loginfo (str "focus out event triggered for: " target-id))
      (cond
       (= target-id "name")
       (do
         (loginfo (str "name->" value))
         (clear-name-error)
         (when-user-exists value
                           (fn [data]
                             (do
                               (loginfo (pr-str "User " value " exists already!"))
                               (set-name-error "name_not_available_error")))))
       (= target-id "email")
       (do
         (loginfo (str "email->" value))
         (clear-email-error)
         (when-user-exists value
                           (fn [data]
                             (do
                               (loginfo (pr-str "Emailaddress " value " exists already!"))
                               (set-email-error "email_defined_error"))))
         (when (not (validate-email value))
           (loginfo (pr-str "Emailaddress " value " is malformed!"))
           (set-email-error "email_malformed_error")))
       (= target-id "password")
       (do
         (loginfo (str "password->" value))
         (clear-password-error)
         (if (< (count value) 5)
           (do
             (loginfo (pr-str "Password " value " too short"))
             (set-password-error "password_form_error"))))
       (= target-id "password-repeat")
       (do
         (loginfo (str "password-repeat->" value))
         (clear-password-repeat-error)
         (if (not= value (.value (dom/get-element "password")))
           (do
             (loginfo (pr-str "Password " value " do not match!"))
             (set-password-repeat-error "password_mismatch_error"))
           ))
       )
      (update-confirm-button-state)))


  (defn- check-all-reg-fields
    "checks all register pane fields.
       This is required before final transmission"
    []
    (dorun (map
            #(updateRegisterText
              (goog.events.Event. "focusout" (dom/get-element %)))
            ["name" "email" "password" "password-repeat"]))
    (update-confirm-button-state))


  (def reg-field-poll-timer (goog.Timer. 500))

  (defn- start-polling-all-reg-field-checks
    "start fields checking every 500ms to send button to be updated
       even when last input field has not been left."
    []
    (do  (. reg-field-poll-timer (start))
         (events/listen reg-field-poll-timer
                        goog.Timer/TICK check-all-reg-fields)))

  (defn- stop-polling-all-reg-field-checks
    "stops field checking"
    []
    (. reg-field-poll-timer (stop)))


  (defn- trigger-polling-when-entered-last-field
    [e]
    (let [target (.target e)
          target-id (.id target)
          target-elem (dom/get-element target-id)
          value (.value target-elem)]
      (if
          (= target-id "password-repeat")
        (start-polling-all-reg-field-checks)
        (stop-polling-all-reg-field-checks))))

  (def register-pane-elem (dom/get-element "register"))
  (def registerFieldsFocusHandler (goog.events.FocusHandler. register-pane-elem))

  (events/listen
   registerFieldsFocusHandler
   goog.events.FocusHandler.EventType.FOCUSOUT
   updateRegisterText)

  (events/listen
   registerFieldsFocusHandler
   goog.events.FocusHandler.EventType.FOCUSIN
   trigger-polling-when-entered-last-field)



  ;; registration failed dialog
  (defn- open-registration-failed-dialog
    "opens when registration failed e.g. due to
   communication error, not implemented yet."
    []
    (open-modal-dialog register-com-error-dialog))


  ;; advice user to check email for registration link
  (defn- open-register-confirm-advice-dialog
    "opens when registration was succeessful and instructs
   user to check email and click registration link."
    []
    (open-modal-dialog register-confirm-advice-dialog))


  ;; clojurescript based event processing
  (def register-confirm-reactor
    (dispatch/react-to
     #{:registration-dialog-confirmed}
     (fn [evt data]
       (if (check-all-reg-fields)
         (let [name (.value (dom/get-element "name"))
               email (.value (dom/get-element "email"))
               password (.value (dom/get-element "password"))]
           (send-request "/register"
                         (json/generate {"name" name
                                         "email" email
                                         "password" password})
                         (fn [ajax-evt]
                           (let [resp (. (.target ajax-evt) (getResponseText))]
                             (dispatch/fire :register-resp
                                            {:name name :email email :resp resp})))
                         "POST"))))))


  (def register-resp-reactor
    (dispatch/react-to
     #{:register-resp}
     (fn [evt data]
       (let [{:keys [name resp]} data]
         (condp = resp
           "OK" (do (dispatch/fire :changed-login-state {:state :registered})
                    (open-register-confirm-advice-dialog))
           (open-registration-failed-dialog))))))


  ;; exports
  (defn open-register-dialog
    "opens the register new user dialog"
    []
    (open-modal-dialog dialog))

  ) ;; (when register-pane



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
  )



(defn- open-login-failed-dialog
  "opens the login failed dialog"
  []
  (open-modal-dialog login-failed-dialog))


(defn- open-login-user-not-confirmed-dialog
  "opens the user is not confirmed dialog"
  []
  (open-modal-dialog login-user-not-confirmed))



;;; clojurescript based event processing

(def login-confirm-reactor
  (dispatch/react-to
   #{:login-dialog-confirmed}
   (fn [evt data]
     (let [name (.value (dom/get-element "login-name"))
           password (.value (dom/get-element "login-password"))]
       (send-request "/login"
                     (json/generate {"name" name "password" password})
                     (fn [ajax-evt]
                       (let [resp (. (.target ajax-evt) (getResponseText))]
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
         (open-login-failed-dialog))))))


;;; exports

(defn open-login-dialog
  "opens the login dialog"
  []
  (open-modal-dialog login-dialog))


(defn send-logout-request
  "sends logout indication to server and change
   to logout state when positive response arrives."
  []
  (send-request "/logout" ""
                (fn [e] (let [xhr (.target e)
                              resp (. xhr (getResponseText))]
                          (if (= resp "OK")
                            (dispatch/fire :changed-login-state
                                           {:state :logout}))))
                "POST"))

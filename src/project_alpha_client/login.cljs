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

(ns project-alpha-client.login
  (:require [clojure.browser.dom :as dom]
            [goog.events :as events]
            [project-alpha-client.json :as json]
            [project-alpha-client.dispatch :as dispatch])
  (:use [project-alpha-client.logging :only [loginfo]]
        [project-alpha-client.utils :only [send-request get-modal-dialog]]))


;;; login dialog

(def login-dialog (get-modal-dialog "login"))

(. login-dialog (setTitle
           (goog.dom.getTextContent (dom/get-element "login-dialog-title"))))
(. login-dialog (setButtonSet null))

(def confirm-login-button (goog.ui.decorate (dom/get-element "confirm-login")))
(. confirm-login-button (setEnabled true))

(events/listen confirm-login-button
               "action"
               (fn [button-evt]
                 (let [name (.value (dom/get-element "login-name"))
                       password (.value (dom/get-element "login-password"))]
                   (send-request "/login"
                                 (json/generate {"name" name "password" password})
                                 (fn [ajax-evt]
                                   (let [resp (. (.target ajax-evt) (getResponseText))]
                                     (dispatch/fire :login-resp
                                                    {:name name :resp resp})))
                                 "POST")
                   (. login-dialog (setVisible false)))))


;;; login failed dialog

(def login-failed-dialog (get-modal-dialog "login-failed"))

(. login-failed-dialog (setTitle
           (goog.dom.getTextContent (dom/get-element "login-failed-dialog-title"))))
(. login-failed-dialog (setButtonSet null))

(def confirm-login-failed-button (goog.ui.decorate (dom/get-element "confirm-login-failed")))
(. confirm-login-failed-button (setEnabled true))

(defn- open-login-failed-dialog
  "opens the login failed dialog"
  []
  (. login-failed-dialog (setVisible true)))

(defn- hide-login-failed-diag [] (. login-failed-dialog (setVisible false)))

(events/listen confirm-login-failed-button "action" hide-login-failed-diag)


;;; login user not confirmed dialog

(def login-user-not-confirmed (get-modal-dialog "login-user-not-confirmed"))

(. login-user-not-confirmed (setTitle
           (goog.dom.getTextContent (dom/get-element "login-user-not-confirmed-title"))))
(. login-user-not-confirmed (setButtonSet null))

(def login-not-confirmed-button (goog.ui.decorate (dom/get-element "confirm-login-user-not-confirmed")))
(. login-not-confirmed-button (setEnabled true))

(defn- open-login-user-not-confirmed-dialog
  "opens the user is not confirmed dialog"
  []
  (. login-user-not-confirmed (setVisible true)))

(defn- hide-login-not-confirmed [] (. login-user-not-confirmed (setVisible false)))

(events/listen login-not-confirmed-button "action" hide-login-not-confirmed)


;;; global event processing

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
  (. login-dialog (setVisible true)))


(defn send-logout-request
  "sends logout indication to server and change
   to logout state when positive response arrives."
  []
  (send-request "/logout" ""
                (fn [e] (let [xhr (.target e)
                              resp (. xhr (getResponseText))]
                          (if (= resp "OK")
                            (dispatch/fire :changed-login-state {:state :logout}))))
                "POST"))

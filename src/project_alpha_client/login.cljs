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
            [project-alpha-client.json :as json])
  (:use [project-alpha-client.logging :only [loginfo]]
        [project-alpha-client.utils :only [send-request get-modal-dialog]]))


(def dialog (get-modal-dialog "login"))

(. dialog (setTitle
           (goog.dom.getTextContent (dom/get-element "login-dialog-title"))))
(. dialog (setButtonSet null))

(def confirm-login-button (goog.ui.decorate (dom/get-element "confirm-login")))
(. confirm-login-button (setEnabled true))

(def login-response-handler (fn [e] nil))


(events/listen confirm-login-button
               "action"
               #(do (send-request "/login"
                                  (json/generate {"name" (.value (dom/get-element "login-name"))
                                                  "password" (.value (dom/get-element "login-password"))})
                                  login-response-handler
                                  "POST")
                    (. dialog (setVisible false))))


(defn set-login-response-handler
  "defines the function which when server response
   to login data is received."
  [handler]
  (def login-response-handler handler))


(defn open-login-dialog
  "opens the login dialog"
  []
  (. dialog (setVisible true)))

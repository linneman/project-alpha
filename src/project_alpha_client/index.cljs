;;;
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; functions for very first page index.html
;;; requires the html/javascript blocks login and register
;;;
;;; 2011-11-23, Otto Linnemann

(ns project-alpha-client.index
  (:require [clojure.browser.dom :as dom]
            [goog.events :as events])
  (:use [project-alpha-client.login :only [open-login-dialog
                                           set-login-response-handler]]
        [project-alpha-client.register :only [open-register-dialog
                                              set-register-response-handler]]
        [project-alpha-client.logging :only [loginfo]]
        [project-alpha-client.auth :only [authenticated? registered?]]
        [project-alpha-client.utils :only [send-request
                                           validate-email
                                           copy-id-text
                                           clear-id-text]]))


(def login-button (goog.ui.decorate (dom/get-element "login-button")))
(events/listen login-button "action" open-login-dialog)


(def register-button (goog.ui.decorate (dom/get-element "register-button")))
(events/listen register-button "action" open-register-dialog)


(defn- set-logged-out-state
  "user not logged and not registered"
  []
  (. login-button (setEnabled true))
  (. register-button (setEnabled true)))

(defn- set-registered-state
  "user is still not logged in but already registered"
  []
  (. login-button (setEnabled true))
  (. register-button (setEnabled false)))

(defn- set-login-state
  "user is logged in"
  []
  (. login-button (setEnabled false))
  (. register-button (setEnabled false)))


;;; initialize state of this side
(if (authenticated?)
  (set-login-state)
  (if (registered?)
    (set-registered-state)
    (set-logged-out-state)))



;;; register response handlers
(set-login-response-handler #(let [xhr (.target %)
                                   resp (. xhr (getResponseText))]
                               (if (= resp "OK") (set-login-state))))


(set-register-response-handler #(let [xhr (.target %)
                                   resp (. xhr (getResponseText))]
                               (if (= resp "OK") (set-registered-state))))

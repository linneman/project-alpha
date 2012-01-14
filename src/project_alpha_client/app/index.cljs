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

(ns project-alpha-client.app.index
  (:require [clojure.browser.dom :as dom]
            [goog.events :as events]
            [project-alpha-client.lib.dispatch :as dispatch])
  (:use [project-alpha-client.lib.login :only [open-login-dialog send-logout-request]]
        [project-alpha-client.lib.register :only [open-register-dialog]]
        [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.auth :only [authenticated? registered?]]
        [project-alpha-client.lib.utils :only [send-request
                                           validate-email
                                           copy-id-text
                                           clear-id-text]]))

;;; buttons
(def login-button (goog.ui.decorate (dom/get-element "login-button")))
(def register-button (goog.ui.decorate (dom/get-element "register-button")))
(def logout-button (goog.ui.decorate (dom/get-element "logout-button")))


;;; auth states
(defn- set-logged-out-state
  "user not logged and not registered"
  []
  (. login-button (setEnabled true))
  (. logout-button (setEnabled false))
  (. register-button (setEnabled true)))

(defn- set-registered-state
  "user is still not logged in but already registered"
  []
  (. login-button (setEnabled true))
  (. logout-button (setEnabled false))
  (. register-button (setEnabled false)))

(defn- set-login-state
  "user is logged in"
  []
  (. login-button (setEnabled false))
  (. logout-button (setEnabled true))
  (. register-button (setEnabled false)))


;;; initialize state according to cookie setup
;;; when site is loaded
(if (authenticated?)
  (set-login-state)
  (if (registered?)
    (set-registered-state)
    (set-logged-out-state)))


;;; register button events
(events/listen login-button "action" open-login-dialog)
(events/listen register-button "action" open-register-dialog)
(events/listen logout-button "action" send-logout-request)


;;; register response handlers
(def login-reactor (dispatch/react-to
                    #{:changed-login-state}
                    (fn [evt data]
                      (let [{:keys [state name]} data]
                        (condp = state
                          :login (set-login-state)
                          :logout (set-logged-out-state)
                          :registered (set-registered-state)
                          nil)))))



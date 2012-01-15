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
            [goog.style :as style]
            [goog.events :as events]
            [project-alpha-client.lib.dispatch :as dispatch])
  (:use [project-alpha-client.lib.login :only [open-login-dialog send-logout-request]]
        [project-alpha-client.lib.register :only [open-register-dialog]]
        [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.auth :only [authenticated? registered?]]
        [project-alpha-client.lib.utils :only [send-request
                                               validate-email
                                               copy-id-text
                                               clear-id-text
                                               init-alpha-button
                                               set-alpha-button-enabled]]))

;;; buttons
(def login-button (init-alpha-button "login-button" :login-button-clicked))
(def register-button (init-alpha-button "register-button" :register-button-clicked))
(def logout-button (init-alpha-button "logout-button" :logout-button-clicked))

;;; auth states
(defn- set-logged-out-state
  "user not logged and not registered"
  []
  (set-alpha-button-enabled login-button true)
  (set-alpha-button-enabled logout-button false)
  (set-alpha-button-enabled register-button true))

(defn- set-registered-state
  "user is still not logged in but already registered"
  []
  (set-alpha-button-enabled login-button true)
  (set-alpha-button-enabled logout-button false)
  (set-alpha-button-enabled register-button false))

(defn- set-login-state
  "user is logged in"
  []
  (set-alpha-button-enabled login-button false)
  (set-alpha-button-enabled logout-button true)
  (set-alpha-button-enabled register-button false))


(defn- disable-all-buttons
  "disable all button to avoid that more than one
   modal dialog is opened."
  []
  (set-alpha-button-enabled login-button false)
  (set-alpha-button-enabled logout-button false)
  (set-alpha-button-enabled register-button false))


(defn- update-status
  "initialize state according to cookie setup"
  []
  (if (authenticated?)
    (set-login-state)
    (if (registered?)
      (set-registered-state)
      (set-logged-out-state))))



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


;;; register button events
(def auth-button-reactor (dispatch/react-to
                          #{:login-button-clicked
                            :logout-button-clicked
                            :register-button-clicked
                            :dialog-opened
                            :dialog-closed}
                          (fn [evt data]
                            (condp = evt
                              :login-button-clicked (open-login-dialog)
                              :logout-button-clicked (send-logout-request)
                              :register-button-clicked (open-register-dialog)
                              :dialog-closed (update-status)
                              :dialog-opened (disable-all-buttons)))))


;;; initialize state according to cookie setup
;;; when site is loaded
(update-status)

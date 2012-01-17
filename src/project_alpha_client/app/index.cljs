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
        [project-alpha-client.lib.utils :only [init-alpha-button
                                               set-alpha-button-enabled
                                               is-alpha-button-enabled]]))


;;; the index page (client side equivalent to index.html)
(def index-pane (dom/get-element "index-pane"))

;;; buttons
(def login-button (init-alpha-button "login-button" :login-button-clicked))
(def register-button (init-alpha-button "register-button" :register-button-clicked))
(def logout-button (init-alpha-button "logout-button" :logout-button-clicked))

;;; button panes
(def login-pane (dom/get-element "login-button-pane"))
(def register-pane (dom/get-element "register-button-pane"))
(def logout-pane (dom/get-element "logout-button-pane"))


;;; auth states
(defn- set-logged-out-state
  "user not logged and not registered"
  []
  (style/showElement login-pane true)
  (style/showElement logout-pane false)
  (style/showElement register-pane true))

(defn- set-registered-state
  "user is still not logged in but already registered"
  []
  (style/showElement login-pane true)
  (style/showElement logout-pane false)
  (style/showElement register-pane false))

(defn- set-login-state
  "user is logged in"
  []
  (style/showElement login-pane false)
  (style/showElement logout-pane true)
  (style/showElement register-pane false))


(def button-states (atom []))

(defn- save-button-states
  []
  (let [buttons [login-button logout-button register-button]]
    (reset! button-states (doall (map #(vector (identity %) (is-alpha-button-enabled %)) buttons))))
  )

(defn- disable-buttons
  "disable all button to avoid that more than one
   modal dialog is opened."
  []
  (save-button-states)
  (dorun (map #(set-alpha-button-enabled % false)
              [login-button logout-button register-button])))

(defn- enable-buttons
  "(re)enable all buttons after disabled-buttons
   has been invoked"
  []
  (dorun (map #(set-alpha-button-enabled (first %) (second %))
              @button-states)))


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
                              :dialog-closed (enable-buttons)
                              :dialog-opened (disable-buttons)))))

(def side-enabled-reactor (dispatch/react-to
                           #{:page-switched}
                           (fn [evt data]
                             (if (= (:to data) :index)
                               (enable-index-page)
                               (disable-index-page)))))


;;; initialize state according to cookie setup
;;; when site is loaded directly via
;;; goog.require('project_alpha_client.app.index')
;;;
;;; (update-status)


(defn- enable-index-page
  "shows the index-page and updates the status"
  []
  (update-status)
  (style/setOpacity index-pane 1) ;; important for first load only
  (style/showElement index-pane true)
  (loginfo "index page enabled")
  )


(defn- disable-index-page
  "hides the index-page, activates the status"
  []
  (style/showElement index-pane false)
  )

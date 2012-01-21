;;;
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; functions for page navigation panel
;;;
;;; 2012-01-21, Otto Linnemann

(ns project-alpha-client.app.nav
  (:require [project-alpha-client.lib.pages :as pages]
            [project-alpha-client.lib.radio-button :as radio]
            [clojure.browser.dom :as dom]
            [goog.style :as style]
            [goog.events :as events]
            [project-alpha-client.lib.dispatch :as dispatch]
            [project-alpha-client.lib.json :as json]
            )
  (:use [project-alpha-client.lib.login :only [open-login-dialog send-logout-request]]
        [project-alpha-client.lib.register :only [open-register-dialog]]
        [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.auth :only [authenticated? registered?]]
        [project-alpha-client.lib.utils :only [init-alpha-button
                                               set-alpha-button-enabled
                                               is-alpha-button-enabled]]))


;;; the index page (client side equivalent to index.html)
(def nav-pane (dom/get-element "index-pane"))


(def button-spec
  {:status  { :dom-id-str "nav-status-button"  :action :nav-status-clicked }
   :profile { :dom-id-str "nav-profile-button" :action :nav-profile-clicked }
   :contact { :dom-id-str "nav-contact-button" :action :nav-contact-clicked }
   :logout  { :dom-id-str "nav-logout-button"  :action :nav-logout-clicked }
   :impress { :dom-id-str "nav-impress-button" :action :nav-impress-clicked }})


(def button-group (radio/init-radio-button-group button-spec))


(def page-switched-reactor (dispatch/react-to
                            #{:page-switched}
                            (fn [evt data]
                              (radio/select-radio-button (:to data) button-group))))

; (dispatch/delete-reaction page-switched-reactor)

(def nav-button-reactor (dispatch/react-to
                         #{:nav-status-clicked
                           :nav-profile-clicked
                           :nav-contact-clicked
                           :nav-logout-clicked
                           :nav-impress-clicked}
                           (fn [evt data] (loginfo evt))))
;(dispatch/delete-reaction nav-button-reactor)

(def nav-status-reactor (dispatch/react-to
                         #{:nav-status-clicked}
                           #(pages/switch-to-page :index)))

(def nav-profile-reactor (dispatch/react-to
                         #{:nav-profile-clicked}
                           #(pages/switch-to-page :profile)))

(def nav-logout-reactor (dispatch/react-to
                         #{:nav-logout-clicked}
                         (fn [] (send-logout-request) (pages/switch-to-page :index))))

;(dispatch/delete-reaction nav-status-reactor)
;(dispatch/delete-reaction nav-status-reactor)
;(dispatch/delete-reaction nav-logout-reactor)
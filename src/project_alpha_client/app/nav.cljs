;;;
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the GNU General Public License
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
(def nav-pane (dom/get-element "nav-pane"))


(defn enable-nav-pane
  "shows the status-page"
  []
  (style/setOpacity nav-pane 1) ;; important for first load only
  (style/showElement nav-pane true)
  (loginfo "nav pane enabled")
  )


(defn disable-nav-pane
  "hides the nav-pane"
  []
  (when nav-pane
    (style/showElement nav-pane false)
    (loginfo "nav pane disabled")))


(defn set-nav-pane-login-enabled
  "when state argument true then show status, profile and
   search buttons, otherwise disable them. The latter case
   is required when the user is logged out."
  [state]
  (dorun (map
          #(. (second %) (setEnabled state))
          (select-keys button-group [:status :profile :search]))))


(def button-spec
  {:index { :dom-id-str "nav-start-button"  :action :nav-start-clicked }
   :status  { :dom-id-str "nav-status-button"  :action :nav-status-clicked }
   :profile { :dom-id-str "nav-profile-button" :action :nav-profile-clicked }
   :search  { :dom-id-str "nav-search-button" :action :nav-search-clicked }
   ; :logout  { :dom-id-str "nav-logout-button"  :action :nav-logout-clicked }
   :imprint { :dom-id-str "nav-imprint-button" :action :nav-imprint-clicked }
   })


(def button-group (radio/init-radio-button-group button-spec))


(def page-switched-reactor (dispatch/react-to
                            #{:page-switched}
                            (fn [evt data]
                              (radio/select-radio-button (:to data) button-group))))

; (dispatch/delete-reaction page-switched-reactor)

(def nav-button-reactor (dispatch/react-to
                         #{:nav-start-clicked
                           :nav-status-clicked
                           :nav-profile-clicked
                           :nav-search-clicked
                           :nav-logout-clicked
                           :nav-imprint-clicked}
                           (fn [evt data] (loginfo evt))))
;(dispatch/delete-reaction nav-button-reactor)

(def nav-start-reactor (dispatch/react-to
                         #{:nav-start-clicked}
                           #(pages/switch-to-page :index)))

(def nav-status-reactor (dispatch/react-to
                         #{:nav-status-clicked}
                           #(pages/switch-to-page :status)))

(def nav-profile-reactor (dispatch/react-to
                         #{:nav-profile-clicked}
                           #(pages/switch-to-page :profile)))

(def nav-search-reactor (dispatch/react-to
                         #{:nav-search-clicked}
                           #(pages/switch-to-page :search)))

(def nav-imprint-reactor (dispatch/react-to
                         #{:nav-imprint-clicked}
                           #(pages/switch-to-page :imprint)))

(def nav-logout-reactor (dispatch/react-to
                         #{:nav-logout-clicked}
                         (fn []
                           (send-logout-request))))

;(dispatch/delete-reaction nav-status-reactor)
;(dispatch/delete-reaction nav-status-reactor)
;(dispatch/delete-reaction nav-logout-reactor)


(def logout-reactor (dispatch/react-to
                    #{:changed-login-state}
                    (fn [evt data]
                      (let [{:keys [state name]} data]
                        (condp = state
                          :logout  (pages/switch-to-page :index)
                          nil)))))

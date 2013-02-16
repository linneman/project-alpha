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
  (:require [project-alpha-client.lib.pages :as pages]
            [project-alpha-client.app.nav :as nav]
            [clojure.browser.dom :as dom]
            [goog.style :as style]
            [goog.events :as events]
            [goog.events.EventType :as event-type]
            [project-alpha-client.lib.dispatch :as dispatch])
  (:use [project-alpha-client.lib.login :only [open-login-dialog
                                               open-pw-forgotten-dialog
                                               send-logout-request]]
        [project-alpha-client.lib.register :only [open-register-dialog]]
        [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.auth :only [authenticated? registered?]]
        [project-alpha-client.lib.utils :only [init-alpha-button
                                               set-alpha-button-enabled
                                               is-alpha-button-enabled
                                               get-element
                                               get-modal-dialog open-modal-dialog]]))


;;; the index page (client side equivalent to index.html)
(def index-pane (dom/get-element "index-pane"))

(when index-pane

  ;; instantiate support dialog and ok button
  (let [[dialog ok-button cancel-button]
        (get-modal-dialog
         :panel-id "support-dialog"
         :title-id "support-dialog-title"
         :ok-button-id "confirm-support-dialog"
         :dispatched-event :support-dialog-confirmed)]
    (def support-dialog dialog)
    (def confirm-support-button ok-button))

  ;;; buttons
  (def login-button (init-alpha-button "login-button" :login-button-clicked))
  (def register-button (init-alpha-button "register-button" :register-button-clicked))
  (def logout-button (init-alpha-button "logout-button" :logout-button-clicked))
  (def forgot-password-button (init-alpha-button "forgot-password-button" :forgot-password-button-clicked))
  (def support-button (get-element "support-button" index-pane))

  ;;; button panes
  (def login-pane (get-element "login-button-pane" index-pane))
  (def register-pane (get-element "register-button-pane" index-pane))
  (def logout-pane (get-element "logout-button-pane" index-pane))
  (def forgot-password-pane (get-element "forgot-password-pane" index-pane))

  ;;; auth states
  (defn- set-logged-out-state
    "user not logged and not registered"
    []
    (style/showElement login-pane true)
    (style/showElement logout-pane false)
    (style/showElement register-pane true)
    (style/showElement forgot-password-pane true))

  (defn- set-registered-state
    "user is still not logged in but already registered"
    []
    (style/showElement login-pane true)
    (style/showElement logout-pane false)
    (style/showElement register-pane false)
    (style/showElement forgot-password-pane true))

  (defn- set-login-state
    "user is logged in"
    []
    (style/showElement login-pane false)
    (style/showElement logout-pane true)
    (style/showElement register-pane false)
    (style/showElement forgot-password-pane false)
    ;(pages/switch-to-page-deferred :status)
    )


  (def button-states (atom []))

  (defn- save-button-states
    []
    (let [buttons [login-button logout-button register-button forgot-password-button]]
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

  (defn- init-lang-change-button
    "helper function which initializes the button for
     changing the language"
    []
    (when-not lang-changed-button
      (let [pane (get-element "change-lang-button-pane")
            attributes (. pane -attributes)
            lang-attr (. attributes (getNamedItem "data-lang"))
            lang (. lang-attr -value)]
        (def lang-changed-button pane)
        (events/listen lang-changed-button event-type/CLICK #(do (dispatch/fire :lang-changed lang))))))

  ;; language change button is invoked here
  (init-lang-change-button)

  ;; event listener for language change
  (def lang-changed-reactor (dispatch/react-to
                             #{:lang-changed}
                             (fn [evt data]
                               (loginfo (str "language changed to: " data))
                               (pages/switch-lang data))))


  (defn- update-status
    "initialize state according to cookie setup"
    []
    (if (authenticated?)
      (set-login-state)
      (if (registered?)
        (set-registered-state)
        (set-logged-out-state))))



  ;;; register response handlers
  ;;; state information is fetched from cookie
  (def login-reactor (dispatch/react-to
                      #{:changed-login-state}
                      (fn [evt data]
                        (let [{:keys [state name]} data]
                          (condp = state
                            :login (do (nav/set-nav-pane-login-enabled true)
                                       (pages/switch-to-page :status)
                                       (loginfo "index-page in login state"))
                            :logout (do (nav/set-nav-pane-login-enabled false)
                                        (pages/switch-to-page :index)
                                        (loginfo "index-page in logout state"))
                            :registered (loginfo "index-page in registered state")
                            nil)
                          (update-status)))))


  ;;; register button events
  (def auth-button-reactor (dispatch/react-to
                            #{:login-button-clicked
                              :logout-button-clicked
                              :register-button-clicked
                              :forgot-password-button-clicked
                              :dialog-opened
                              :dialog-closed}
                            (fn [evt data]
                              (condp = evt
                                :login-button-clicked (open-login-dialog)
                                :logout-button-clicked (send-logout-request)
                                :register-button-clicked (open-register-dialog)
                                :forgot-password-button-clicked (open-pw-forgotten-dialog)
                                :dialog-closed (enable-buttons)
                                :dialog-opened (disable-buttons)))))

  (def support-button-reactor (dispatch/react-to
                               #{:support-button-clicked}
                               #(open-modal-dialog support-dialog)))


  ;;;
  ;;; animated support button is handled separately
  ;;;
  (def support-button-opac-max 1)
  (def support-button-opac-min 0.5)
  (def support-button-delta (atom 0.1))
  (def support-button-opac (atom support-button-opac-min))

  (defn- set-support-button-animation-periods
    [periods]
    (reset! support-button-delta
            (/ (- support-button-opac-max support-button-opac-min) periods)))

  (set-support-button-animation-periods 100)
  ;(. (. support-button -style) (setProperty "background-color" "green" "important"))
  ;(. (. support-button -style) (setProperty "background-color" "rgb(136,255,136)" "important"))
  ;(. (. support-button -style) (setProperty "opacity" 1 "important"))

  (defn- animate
    []
    (let [opac (+ @support-button-opac @support-button-delta)
          delta @support-button-delta
          delta (if (> opac support-button-opac-max) (- delta) delta)
          delta (if (< opac support-button-opac-min) (- delta) delta)
          opac (min support-button-opac-max opac)
          opac (max support-button-opac-min opac)]
      (. (. support-button -style) (setProperty "opacity" opac "important"))
      (reset! support-button-opac opac)
      (reset! support-button-delta delta)))


  (def animation-timer (goog.Timer. 10))
  (events/listen animation-timer goog.Timer/TICK animate)

  (defn- start-animation-timer
    []
    (. animation-timer (start)))

  (defn- stop-animation-timer
    []
    (. animation-timer (stop)))


  (events/listen support-button event-type/CLICK #(dispatch/fire :support-button-clicked nil))
  (events/listen support-button event-type/MOUSEOUT #(set-support-button-animation-periods 100))
  (events/listen support-button event-type/MOUSEOVER #(set-support-button-animation-periods 30))

  )  ; (when index-pane


(def site-enabled-reactor (dispatch/react-to
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
  (style/setOpacity index-pane 1) ;; important for first load only
  (style/showElement index-pane true)
  (if (authenticated?)
    (nav/set-nav-pane-login-enabled true)
    (nav/set-nav-pane-login-enabled false))
  (loginfo "index page enabled")
  (update-status)
  (start-animation-timer)
  )


(defn- disable-index-page
  "hides the index-page, activates the status"
  []
  (when index-pane
    (style/showElement index-pane false)
    (stop-animation-timer)))

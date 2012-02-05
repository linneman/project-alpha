;;;
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; functions for profile page
;;;
;;; 2011-11-23, Otto Linnemann

(ns project-alpha-client.app.profile
  (:require [project-alpha-client.lib.pages :as pages]
            [project-alpha-client.app.nav :as nav]
            [project-alpha-client.lib.json :as json]
            [project-alpha-client.lib.editor :as editor]
            [clojure.browser.dom :as dom]
            [goog.dom :as gdom]
            [goog.style :as style]
            [goog.events :as events]
            [goog.ui.Button :as Button]
            [goog.ui.TabPane :as TabPane]
            [project-alpha-client.lib.dispatch :as dispatch])
  (:use [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.utils :only [send-request get-button-group-value
                                               set-button-group-value]]))

;;; the profile page (client side equivalent to profile.html)
(def profile-pane (dom/get-element "profile-pane"))

(when profile-pane

  (def tabpane (goog.ui.TabPane. (dom/get-element "tabpane1")))
  (. tabpane (addPage (TabPane/TabPage. (dom/get-element "page1"))))
  (. tabpane (addPage (TabPane/TabPage. (dom/get-element "page2"))))
  (. tabpane (addPage (TabPane/TabPage. (dom/get-element "page3"))))

  (def editor (editor/create "editMe" "toolbar"))

  (defn get-content
    []
    (merge
     {"text" (. editor (getCleanContents))}
     (get-button-group-value "user_sex")
     (get-button-group-value "user_interest_sex")))


  (events/listen editor goog.editor.Field.EventType.DELAYEDCHANGE
                 (fn [e]
                   ; (loginfo (json/generate {"text" (. editor (getCleanContents))}))
                   (send-request "/profile"
                                 (json/generate (get-content))
                                 (fn [e] nil)
                                 "POST")))

  (defn request-profile-data
    []
    (send-request "/profile"
                  ""
                  (fn [ajax-evt]
                    (let [resp (. (.target ajax-evt) (getResponseText))]
                                        ; (loginfo resp)
                      (dispatch/fire :get-my-profile-resp
                                     (json/parse resp))))))

  (defn update-content
    [data]
    (. editor (setHtml false (data "text") true))
    (set-button-group-value "user_sex" (set [(data "user_sex")]))
    (set-button-group-value "user_interest_sex" (set [(data "user_interest_sex")])))


  (def my-profile-resp-reactor (dispatch/react-to
                                #{:get-my-profile-resp}
                                (fn [evt data]
                                  (update-content data)
                                  )))

  (def site-enabled-reactor (dispatch/react-to
                             #{:page-switched}
                             (fn [evt data]
                               (if (= (:to data) :profile)
                                 (enable-profile-page)
                                 (disable-profile-page)))))

  (defn- enable-profile-page
    "shows or reloads the profile-page"
    []
    (if profile-pane
      (do
        (request-profile-data)
        (style/setOpacity profile-pane 1) ;; important for first load only
        (style/showElement profile-pane true)
        (nav/enable-nav-pane)
        (loginfo "profile page enabled"))
      (do
        (pages/reload-url "/profile.html")
        (loginfo "profile page reloaded"))))


  (defn- disable-profile-page
    "hides the index-page, activates the status"
    []
    (when profile-pane
      (style/showElement profile-pane false)))


  ; (dispatch/delete-reaction my-profile-resp-reactor)
  ; (request-profile-data)

  ; (. editor (setHtml false "Hallo Welt" true))

  )


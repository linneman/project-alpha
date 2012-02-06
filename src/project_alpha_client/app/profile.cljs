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
            [goog.ui.Select :as Select]
            [goog.ui.MenuItem :as MenuItem]
            [project-alpha-client.lib.dispatch :as dispatch])
  (:use [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.utils :only [send-request get-button-group-value
                                               set-button-group-value]]))

;;; the profile page (client side equivalent to profile.html)
(def profile-pane (dom/get-element "profile-pane"))

(when profile-pane


  (def age-ranges
    (for [k (range 15 70 5)]
      (cond (< k 20)
        {:age 18 :sel-string "< 20" :range-min 18 :range-max 19}
        (< k 65)
        {:age (+ k 2) :sel-string (str k "..." (+ k 4))
         :range-min k :range-max (+ k 4)}
        (>= k 65)
        {:age k :sel-string ">= 65" :range-min 65 :range-max 100})))

  (def idx-age-ranges
    (zipmap (range 0 (count age-ranges)) age-ranges))


  (def ageSelect (goog.ui.Select. "..."))
  (dorun
   (map #(. ageSelect (addItem (goog.ui.MenuItem. (:sel-string %))))
        age-ranges))
  (. ageSelect (render (dom/get-element "user-age")))


  (defn- get-selected-age
    "retrieves age from selection field"
    []
    (:age (second (nth idx-age-ranges (. ageSelect (getSelectedIndex))))))

  ; (get-selected-age)


  (defn- set-selected-age
    "updates age in selection field"
    [age]
    (let [entry (filter #(and
                          (>= age (:range-min (second %)))
                          (<= age (:range-max (second %))))
                        idx-age-ranges)]
      (. ageSelect (setSelectedIndex (first (first entry))))))

  ; (set-selected-age 25)


  (def tabpane (goog.ui.TabPane. (dom/get-element "tabpane1")))
  (. tabpane (addPage (TabPane/TabPage. (dom/get-element "page1"))))
  (. tabpane (addPage (TabPane/TabPage. (dom/get-element "page2"))))
  (. tabpane (addPage (TabPane/TabPage. (dom/get-element "page3"))))

  ;;; atom for rembering last active tabpane page
  ;;; unfortunately we do not have an event for this
  (def active-pane-idx (atom (. tabpane (getSelectedIndex))))


  (def editor (editor/create "editMe" "toolbar"))


  (defn get-text-content
    "reads the text field of the profile
     pane. This is done whenever the editor
     sends a new key event in order to avoid
     that the text is lost when the browser
     tab is closed."
    []
    {"text" (. editor (getCleanContents))})


  (defn get-age-and-sex-content
    "reads the out all the the profile content
     page. This is transfered whenever
     the this tab is left."
    []
    (merge
     (get-button-group-value "user_sex")
     (get-button-group-value "user_interest_sex")
     {"user_age" (get-selected-age)}))


  (defn post-disabled-page-content
    "post everything of the profiles pane
     which does not belong to the text edit pane
     which is handled differently."
    [selected-page-idx]
    (let [id-str-left (.id (. (. tabpane (getPage @active-pane-idx))
                              (getContentElement)))]
      (if (= id-str-left "page1") ; page one (age and sex)
        (do
          (send-request "/profile"
                        (json/generate (get-age-and-sex-content))
                        (fn [e] nil)
                        "POST")
          (loginfo "age-and-sex profile data updated")))
      (reset! active-pane-idx selected-page-idx)))


  (events/listen editor goog.editor.Field.EventType.DELAYEDCHANGE
                 (fn [e]
                   ; (loginfo (json/generate {"text" (. editor (getCleanContents))}))
                   (send-request "/profile"
                                 (json/generate (get-text-content))
                                 (fn [e] nil)
                                 "POST")))


  (events/listen tabpane goog.ui.TabPane.Events.CHANGE
                 (fn [e] (let [idx (. (.page e) (getIndex))
                               id-str (.id (. (. tabpane (getPage idx))
                                              (getContentElement)))]
                           (dispatch/fire :profile-tab-changed idx))))


  (def profile-tab-changed-reactor (dispatch/react-to
                                #{:profile-tab-changed}
                                (fn [evt data]
                                  (post-disabled-page-content data)
                                  )))

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
    (set-button-group-value "user_interest_sex" (set [(data "user_interest_sex")]))
    (set-selected-age (data "user_age")))


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
      (post-disabled-page-content @active-pane-idx) ; do not forget to post last active page data
      (style/showElement profile-pane false)))


  ; (dispatch/delete-reaction my-profile-resp-reactor)
  ; (request-profile-data)

  ; (. editor (setHtml false "Hallo Welt" true))


  )


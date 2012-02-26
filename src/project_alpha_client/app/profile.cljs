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
            [clojure.string :as string]
            [goog.dom :as gdom]
            [goog.dom :as gdom]
            [goog.style :as style]
            [goog.events :as events]
            [goog.ui.Button :as Button]
            [goog.ui.TabPane :as TabPane]
            [goog.ui.Select :as Select]
            [goog.ui.Menu :as Menu]
            [goog.ui.SubMenu :as SubMenu]
            [goog.ui.PopupMenu :as PopupMenu]
            [goog.ui.MenuItem :as MenuItem]
            [goog.positioning.Corner :as Corner]
            [project-alpha-client.lib.dispatch :as dispatch])
  (:use [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.utils :only [send-request get-button-group-value
                                               set-button-group-value]]))

;;; the profile page (client side equivalent to profile.html)
(def profile-pane (dom/get-element "profile-pane"))

(when profile-pane

  ;;; --- age selection helpers ---

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


  ;;; definition of the age selection pane
  (def ageSelect (goog.ui.Select. "..."))
  (dorun
   (map #(. ageSelect (addItem (goog.ui.MenuItem. (:sel-string %))))
        age-ranges))
  (. ageSelect (setSelectedIndex -1))
  (. ageSelect (render (dom/get-element "user-age")))
  ;; adjust the width of the outer button pane
  (set! (. (. (. ageSelect (getElement)) -style) -width)
        (. (dom/get-element "user-open-zip-button") -clientWidth))


  (defn- get-selected-age
    "retrieves age from selection field"
    []
    (let [idx (. ageSelect (getSelectedIndex))]
      (when (>= idx 0)
        { "user_age" (:age (second (nth idx-age-ranges idx))) })))

  ; (get-selected-age)


  (defn- set-selected-age
    "updates age in selection field"
    [age]
    (let [entry (filter #(and
                          (>= age (:range-min (second %)))
                          (<= age (:range-max (second %))))
                        idx-age-ranges)]
      (. ageSelect (setSelectedIndex (first (first entry))))))


  ; --- the tab and the editor pane ---

  (def tabpane (goog.ui.TabPane. (dom/get-element "tabpane1")))
  (. tabpane (addPage (TabPane/TabPage. (dom/get-element "page1"))))
  (. tabpane (addPage (TabPane/TabPage. (dom/get-element "page2"))))
  (. tabpane (addPage (TabPane/TabPage. (dom/get-element "page3"))))
  (. tabpane (addPage (TabPane/TabPage. (dom/get-element "page4"))))
  (. tabpane (addPage (TabPane/TabPage. (dom/get-element "page5"))))

  ;;; atom for rembering last active tabpane page
  ;;; unfortunately we do not have an event for this
  (def active-pane-idx (atom (. tabpane (getSelectedIndex))))

  (def editor (editor/create "editMe" "toolbar"))


  ; --- selection of the zip code ---

  (defn split-zip-from-cities
    "reads comma (resp. space) separated list string
       with the zip code as first entry and a number
       of locations (cities) as the following entries
       and retursn zip and cities splitted"
    [zip-str]
    (let [zip (first (re-seq #"^[0-9]+" zip-str))
          cities (string/trim (first  (re-seq #"[^0-9]+" zip-str)))]
      [zip cities]))


  (def zipMenuDiv (dom/get-element "user-zip"))

  ;; builds the hash with zip codes as key and city list
  ;; as values before the construction of the zipMenu
  (def zip-cities-hash
    (apply merge (map (fn [loc-str]
                        (let [[zip cities] (split-zip-from-cities
                                            (. loc-str -textContent))]
                          {zip cities}))
                      (gdom/findNodes zipMenuDiv
                                      (fn [e] (= "goog-menuitem" (. e -className)))))))

  (def zipMenu (goog.ui.PopupMenu.))
  (def zipMenuButtonDiv (dom/get-element "user-open-zip-button"))
  (def zipMenuButton (goog.ui.decorate zipMenuButtonDiv))
  (. zipMenuButton (setEnabled true))

  (. zipMenu (decorate zipMenuDiv))
  (. zipMenu (attach
              zipMenuButtonDiv
              goog.positioning.Corner.BOTTOM_START))
  (events/listen zipMenu "action"
                 (fn [e] (dispatch/fire :zip-menu-changed-reactor
                                        (. (. e -target) (getCaption))) ))


  (def zip-menu-changed-reactor (dispatch/react-to
                                #{:zip-menu-changed-reactor}
                                (fn [evt data]
                                  (zip-menu-changed data)
                                  )))


  ;;; for storing last selected zip code
  (def current-zip-code (atom {}))

  (defn update-zip-code
    "called whenever zip view needs to be updated"
    [zip]
    (let [cities (zip-cities-hash zip)]
      (reset! current-zip-code zip)
      (. zipMenuButton (setCaption (str "PLZ Gebiet " zip)))
      (set! (. (dom/get-element "user-zip-city-list") -textContent) cities)))


  (defn zip-menu-changed
    "called when zip code was changed by user"
    [data]
    (let [[zip cities] (split-zip-from-cities data)]
      (update-zip-code zip)))


  (defn get-zip-code
    "reads out the selected zip code (for ajax post)
     sets longitude and latitude to invalid values
     to indicate that these values need to be updated
     on the server."
    []
    (when-not (empty? @current-zip-code)
      {"user_country_code" "de"
       "user_zip" @current-zip-code
       "user_lon" 360
       "user_lat" 360}))


  (comment
    (dispatch/delete-reaction
     zip-menu-changed-reactor)
    )


  ; --- getters for ajax POST ---

  (defn get-text-content
    "reads the text field of the profile
     pane. This is done whenever the editor
     sends a new key event in order to avoid
     that the text is lost when the browser
     tab is closed."
    []
    (let [text (. editor (getCleanContents))]
      (if text {"text" text})))


  (defn get-age-and-sex-content
    "reads the out all the the profile content
     page. This is transfered whenever
     the this tab is left."
    []
    (merge
     (get-button-group-value "user_sex")
     (get-button-group-value "user_interest_sex")
     (get-selected-age)
     (get-zip-code)))

  (defn- get-questionnaire-answers
    "reads the answers of the list of questions
     on the 3td pane."
    []
    (apply merge
           (let [quest-nr-list (range 1 11)]
             (map #(get-button-group-value (str "question_" %)) quest-nr-list))))


  (defn- get-page-id-str
    "returns the dom-id str for the tabe pane page
     with given index which is starting from 0."
    [idx]
    (let [page (. tabpane (getPage idx))
          elem (. page (getContentElement))]
      (. elem -id)))

  (defn- get-fav-list
    "reads the entries of the favorite book list on the 4th pane."
    [author-dom-str title-dom-str]
    (let [idx (range 1 4)
          fav_auth_keys (map #(str author-dom-str %) idx)
          fav_auth_vals (map #(. (dom/get-element %) -value) fav_auth_keys)
          fav_title_keys (map #(str title-dom-str %) idx)
          fav_title_vals (map #(. (dom/get-element %) -value) fav_title_keys)
          data (vec (map
                     #(hash-map "author" %1 "title" %2 "rank" %3)
                     fav_auth_vals fav_title_vals idx))]
      data))

  ;(get-fav-books)


  ; --- update functions trigger by ajax GET ---

  (defn- post-as-json
    [hash-to-post]
    (send-request "/profile"
                        (json/generate hash-to-post)
                        (fn [e] nil)
                        "POST"))

  (defn- update-tab-panes
    "post everything of the profiles pane
     which does not belong to the text edit pane
     which is handled differently.
     furthermore enable and disable the editor according
     to the active pane due to a problem in firefox."
    [selected-page-idx]
    (let [id-str-left (get-page-id-str @active-pane-idx)
          id-str-active (get-page-id-str selected-page-idx)]
      (if (= id-str-active "page2") ; firefox does not support to active when not displayed
        (when (. editor (isUneditable)) (. editor (makeEditable)))
        (when-not (. editor (isUneditable)) (. editor (makeUneditable)))
        )
      (if (= id-str-left "page1") ; post page one content (age and sex) when left
        (do
          (post-as-json (get-age-and-sex-content))
          (loginfo "age-and-sex profile data posted")))
      (if (= id-str-left "page3") ; post questionaire when page left
        (do
          (post-as-json (get-questionnaire-answers))
          (loginfo "questionnaire data posted"))
        )
      (if (= id-str-left "page4")
        (do
          (post-as-json {"fav_books"
                         (get-fav-list "user_fav_auth_" "user_fav_book_")})
          (post-as-json {"fav_movies"
                         (get-fav-list "user_fav_director_" "user_fav_movie_")})
          (loginfo "favorite lists posted")))
      (reset! active-pane-idx selected-page-idx)))


  ; --- ajax event handling ---

  (events/listen editor goog.editor.Field.EventType.DELAYEDCHANGE
                 (fn [e]
                   ; (loginfo (json/generate {"text" (. editor (getCleanContents))}))
                   (post-as-json (get-text-content))))

  (events/listen tabpane goog.ui.TabPane.Events.CHANGE
                 (fn [e] (let [idx (. (. e -page) (getIndex))
                               id-str (. (. (. tabpane (getPage idx))
                                              (getContentElement)) -id)]
                           (dispatch/fire :profile-tab-changed idx))))


  (def profile-tab-changed-reactor (dispatch/react-to
                                #{:profile-tab-changed}
                                (fn [evt data]
                                  (update-tab-panes data)
                                  )))

  (defn- request-profile-data
    []
    (send-request "/profile"
                  ""
                  (fn [ajax-evt]
                    (let [resp (. (. ajax-evt -target) (getResponseText))]
                                        ; (loginfo resp)
                      (dispatch/fire :get-my-profile-resp
                                     (json/parse resp))))))


  (defn- update-questionnaire
    "updates the displayed questionnaire answer list"
    [data]
    (let [quest-nr-list (range 1 11)]
      (dorun (map
              (fn [elem]
                (let [key (str "question_" elem)
                      val (set [(str (data key))])]
                  (set-button-group-value key val))) quest-nr-list))))


  (defn- update-fav-list
    "updates the displayed entries of the favorite book
   list on the 4th pane."
    [data author-dom-str title-dom-str]
    (let [idx (range 1 4)
          fav_auth_keys (map #(str author-dom-str %) idx)
          fav_auth_vals (map #(dom/get-element %) fav_auth_keys)
          fav_title_keys (map #(str title-dom-str %) idx)
          fav_title_vals (map #(dom/get-element %) fav_title_keys)
          rank-book (apply merge
                           (map #(hash-map (% "rank")
                                           {:author (% "author") :title (% "title")})
                                data))]
      (doseq [[auth title idx] (map list fav_auth_vals fav_title_vals idx)]
        (when rank-book
          (set! (. auth -value) (:author (rank-book idx)))
          (set! (. title -value) (:title (rank-book idx)))))))


  (defn- update-content
    "update the displayed profile page content
     which is triggered after an ajax get request. Disables
     progress pane afterwords."
    [data]
    (when-let [txt (data "text")] (. editor (setHtml false txt true)))
    (set-button-group-value "user_sex" (set [(data "user_sex")]))
    (set-button-group-value "user_interest_sex" (set [(data "user_interest_sex")]))
    (set-selected-age (data "user_age"))
    (update-zip-code (data "user_zip"))
    (update-questionnaire data)
    (update-fav-list (data "user_fav_books") "user_fav_auth_" "user_fav_book_")
    (update-fav-list (data "user_fav_movies") "user_fav_director_" "user_fav_movie_")
    (style/showElement (dom/get-element "profile_request_progress") false))


  (def my-profile-resp-reactor (dispatch/react-to
                                #{:get-my-profile-resp}
                                (fn [evt data]
                                  (when data (update-content data))
                                  )))

  ) ; (when profile-pane


; --- site enabling and disabling ---

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
    (update-tab-panes @active-pane-idx) ; do not forget to post last active page data
    (style/showElement profile-pane false)))


; (dispatch/delete-reaction my-profile-resp-reactor)
; (request-profile-data)

;;;
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; functions for the search page
;;;
;;; 2011-11-23, Otto Linnemann

(ns project-alpha-client.app.search
  (:require [project-alpha-client.lib.pages :as pages]
            [project-alpha-client.app.nav :as nav]
            [project-alpha-client.lib.json :as json]
            [project-alpha-client.lib.editor :as editor]
            [clojure.browser.dom :as dom]
            [clojure.string :as string]
            [goog.dom :as gdom]
            [goog.style :as style]
            [goog.events :as events]
            [goog.ui.Button :as Button]
            [goog.ui.ButtonRenderer :as ButtonRenderer]
            [goog.ui.FlatButtonRenderer :as FlatButtonRenderer]
            [goog.ui.TabPane :as TabPane]
            [goog.Timer :as timer]
            [project-alpha-client.lib.dispatch :as dispatch])
  (:use [project-alpha-client.lib.table-controller
         :only [init-sortable-search-result-table
                release-sortable-search-result-table
                render-table-button
                create-test-data]]
        [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.utils :only [send-request get-element
                                               init-alpha-button get-element]]))


;;; the profile page (client side equivalent to index.html)
(def search-pane (dom/get-element "search-pane"))


(when search-pane


  ;; --- sortable search result table ---
  (defn unitstr2num [string] (apply js/Number (re-seq #"-?[\d.]+" string)))
  (defn german-date-str-to-ms
    [datestr]
    (let [[day month year] (map js/Number (. datestr (split ".")))]
      (. (js/Date. year month day) (getTime))))

  (def user-details-reactor (dispatch/react-to
                        #{:show-user-details}
                        (fn [evt data]
                          (loginfo (str "detail button pressed for user id: " data)))))


  ;; the result table objects are initialized when firstly clicked
  ;; too high initial delay
  (def result-table-atom (atom nil))
  (def favorite-table-atom (atom nil))


  (defn- render-table
    [table-id controller-id data]
    (init-sortable-search-result-table controller-id table-id data 10
     {"sort-by-date" (partial sort #(compare
                                     (german-date-str-to-ms (first %1))
                                     (german-date-str-to-ms (first %2))))
      "sort-by-name" (partial sort #(compare (second %1) (second %2)))
      "sort-by-dist" (partial sort #(compare
                                     (unitstr2num (nth %1 2))
                                     (unitstr2num (nth %2 2))))
      "sort-by-match" (partial sort #(compare
                                      (unitstr2num (nth %2 3))
                                      (unitstr2num (nth %1 3))))}))


  (defn- request-result-pane [force-update]
    (when force-update
      (when @result-table-atom
        (release-sortable-search-result-table @result-table-atom)
        (reset! result-table-atom nil)))
    (when-not @result-table-atom
      (style/showElement (dom/get-element
                          "search_request_progress") true)
      (timer/callOnce ; later on XHTTP
       (fn []
         (reset! result-table-atom
                 (render-table
                  "search-result-table"
                  "search-result-controller"
                  (create-test-data)))
         (style/showElement (dom/get-element
                             "search_request_progress") false))
       10)))


  (defn- request-favorite-pane [force-update]
    (when force-update
      (when @favorite-table-atom
        (release-sortable-search-result-table @favorite-table-atom)
        (reset! favorite-table-atom nil)))
    (when-not @favorite-table-atom
      (style/showElement (dom/get-element
                          "search_request_progress") true)
      (timer/callOnce ; later on XHTTP
       (fn []
         (reset! favorite-table-atom
                 (render-table
                  "favorite-table"
                  "favorite-controller"
                  (take 15 (create-test-data))))
         (style/showElement (dom/get-element
                             "search_request_progress") false))
       10)))



  ; --- the tab pane ---

  (def tabpane (goog.ui.TabPane. (get-element "tabpane-search" search-pane)))
  (. tabpane (addPage (TabPane/TabPage. (get-element "search-results" search-pane))))
  (. tabpane (addPage (TabPane/TabPage. (get-element "favorites" search-pane))))
  (. tabpane (addPage (TabPane/TabPage. (get-element "search-setup" search-pane))))


  ; --- tabpane event handling ---

  (defn- get-page-id-str
    "returns the dom-id str for the tabe pane page
     with given index which is starting from 0."
    [idx]
    (let [page (. tabpane (getPage idx))
          elem (. page (getContentElement))]
      (. elem -id)))

  (events/listen tabpane goog.ui.TabPane.Events.CHANGE
                 (fn [e] (let [idx (. (. e -page) (getIndex))
                               id-str (. (. (. tabpane (getPage idx))
                                              (getContentElement)) -id)]
                           (dispatch/fire :search-tab-changed idx))))


  (defn- update-tab-panes
    "renders the selected pane. This is done when pane is clicked first
     to avoid too long initial delay"
    [pane-id-str]
    (condp = pane-id-str
      "favorites" (request-favorite-pane false)
      "search-results" (request-result-pane false)
      "search-setup" (loginfo "search-setup")))


  (def search-tab-changed-reactor
    (dispatch/react-to
     #{:search-tab-changed}
     (fn [evt data]
       (update-tab-panes (get-page-id-str data)))))


  ;; trigger initial pane changed event
  (dispatch/fire :search-tab-changed (. tabpane (getSelectedIndex)))



  (comment use the method below to release resources

    (dispatch/delete-reaction search-tab-changed-reactor)
    (release-sortable-search-result-table @result-table-atom)
    (reset! result-table-atom nil)

    )


) ; (when search-pane




; --- site enabling and disabling ---

(def site-enabled-reactor (dispatch/react-to
                           #{:page-switched}
                           (fn [evt data]
                             (if (= (:to data) :search)
                               (enable-search-page)
                               (disable-search-page)))))

(defn- enable-search-page
  "shows the search-page"
  []
  (if search-pane
    (do
      (style/setOpacity search-pane 1) ;; important for first load only
      (style/showElement search-pane true)
      (nav/enable-nav-pane)
      (loginfo "search page enabled"))
    (do
      (pages/reload-url "/search.html")
      (loginfo "search page reloaded"))))


(defn- disable-search-page
  "hides the search-page, activates the search"
  []
  (when search-pane
    (style/showElement search-pane false)
    (loginfo "search page disabled")))

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
            [project-alpha-client.lib.dispatch :as dispatch])
  (:use [project-alpha-client.lib.table-controller
         :only [init-sortable-search-result-table
                release-sortable-search-result-table
                render-table-button
                create-test-data]]
        [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.utils :only [send-request get-element
                                               init-alpha-button]]))


;;; the profile page (client side equivalent to index.html)
(def search-pane (dom/get-element "search-pane"))


(when search-pane

  (defn unitstr2num [string] (apply js/Number (re-seq #"-?[\d.]+" string)))
  (defn german-date-str-to-ms
    [datestr]
    (let [[day month year] (map js/Number (. datestr (split ".")))]
      (. (js/Date. year month day) (getTime))))

  (def user-details-reactor (dispatch/react-to
                        #{:show-user-details}
                        (fn [evt data]
                          (loginfo (str "detail button pressed for user id: " data)))))

  (def sortable-result-table
    (init-sortable-search-result-table
     "search-result-controller"
     "search-result-table" (create-test-data) 10
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


  (comment use the method below to release resources

    (release-sortable-search-result-table sortable-result-table)

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

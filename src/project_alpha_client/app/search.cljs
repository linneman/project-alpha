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
            [goog.dom :as gdom]
            [goog.style :as style]
            [goog.events :as events]
            [goog.ui.Button :as Button]
            [goog.ui.TabPane :as TabPane]
            [project-alpha-client.lib.dispatch :as dispatch])
  (:use [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.utils :only [send-request get-element]]))

;;; the profile page (client side equivalent to index.html)
(def search-pane (dom/get-element "search-pane"))


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



(defn htmlcoll2array
  "Transforms a HTMLCollection into a clojure array"
  [htmlcol]
  (loop [index 0 row-array []]
    (if-let [row (. htmlcol (item index))]
      (recur (inc index) (conj row-array row))
      row-array)))


(defn clear-table
  "Removes all rows except the prototype-row from an html
   table with a given dom id string."
  [table-id-str]
  (let [table (dom/get-element "search-result-table")
        table-body (gdom/getFirstElementChild table)
        rows (htmlcoll2array (gdom/getChildren table-body))]
    (doseq [row rows]
      (when-not (= "prototype-row" (. row -id))
        (gdom/removeNode row)))))


(defn render-table
  "Renders an html table with the given dom id string and
   an array of table rows where each table row is in turn
   an array of column elements. The html table is expected
   to have one prototype row which is cloned for each new
   row which is added to the array"
  [table-id-str data-arr]
  (let [table (dom/get-element table-id-str)
        table-body (gdom/getFirstElementChild table)
        prototype-row (get-element "prototype-row" table-body)
        data-arr (map vector (iterate inc 1) data-arr)]
    (doseq [[row-idx row-data] data-arr]
      (let [row-data (map vector (iterate inc 1) row-data)
            new-row (. prototype-row (cloneNode true))]
        (set! (. new-row -id) (str "row" row-idx))
            ; (println "row-elem: " new-row)
        (doseq [[col-idx col-data] row-data]
          (let [col-elem (get-element (str "col" col-idx) new-row)]
            ; (println "col-id-str: " (str "col" col-idx) "col-elem: " col-elem)
            (set! (. col-elem -innerHTML) col-data)
            ))
        (gdom/appendChild table-body new-row)))))


(comment

  (clear-table "search-result-table")
  (render-table "search-result-table"
                [["Karl" "100km" "57%"]
                 ["Anton" "70km" "68%"]])

  (render-table "search-result-controller"
                [["1..5" "6..10" "11..15" "16..20" ">>"]])


)



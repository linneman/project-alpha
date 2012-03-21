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
            [goog.ui.ButtonRenderer :as ButtonRenderer]
            [goog.ui.FlatButtonRenderer :as FlatButtonRenderer]
            [goog.ui.TabPane :as TabPane]
            [project-alpha-client.lib.dispatch :as dispatch])
  (:use [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.utils :only [send-request get-element]]))

;;; the profile page (client side equivalent to index.html)
(def search-pane (dom/get-element "search-pane"))


(when search-pane

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
    (let [table (dom/get-element table-id-str)
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
              (if (string? col-data)
                (set! (. col-elem -innerHTML) col-data)
                (. col-data (render col-elem)))))
          (gdom/appendChild table-body new-row)))))


  (defn new-page-crtl-button
    "creates instance of new page controller button with
   given label. When given an event parameter is given
   it will be attached to this button within the field
   evt-params."
    ([label] (new-page-crtl-button label nil))
    ([label params]
       (let [button (goog.ui.Button. label (FlatButtonRenderer/getInstance))]
         (set! (. button -evt-params) params)
         (events/listen button "action"
                        #(do
                           (comment
                             (loginfo (str "button in table: "
                                           (. button -table-controller)
                                           " with data: " (pr-str (. button -evt-params))
                                           " pressed!")))
                           (dispatch/fire (. button -table-controller)
                                          (. button -evt-params))))
         button)))


  (defn create-page-crtl-button-groups
    "instantiates vector of buttons where each button refers to
   a group of search results presented in a row. The number of
   buttons are specified with the first argument. Not all of
   them are necessarily used later on. The second argument
   specifies the number of rows in the corresponding search
   result table. The last argument specifies the starting index.
   If not specified the first search result starts with the
   index one."
    ([nr-buttons nr-rows] (create-page-crtl-button-groups nr-buttons nr-rows 1))
    ([nr-buttons nr-rows start-idx]
       (let [start-indices (take nr-buttons
                                 (iterate #(+ % nr-rows) start-idx))]
         (map
          (fn [start-idx]
            (let [label (str start-idx ".." (+ start-idx nr-rows -1))]
              (new-page-crtl-button label {:start-idx start-idx :nr-rows nr-rows})))
          start-indices))))


  (defn create-page-crtl-buttons
    "instantiates page controll buttons where first element
   to be displayed starts at given index. When first element
   is not in displayed range a '<' button is appended for
   jumping back. If last element is not in the range of
   specified elements (nr-buttons, nr-rows) a button with
   the label '>' is rendered on the right hand side for
   jumping to next result group."
    [start-idx last-idx nr-buttons nr-rows]
    (let [first-entries (when (> start-idx 1)
                          (let [next-start-idx (- start-idx (* nr-rows (- nr-buttons 2)))
                                next-start-idx (if (< next-start-idx 1) 1 next-start-idx)
                                nav {:render-crtl true
                                     :start-idx next-start-idx :last-idx last-idx
                                     :nr-buttons nr-buttons :nr-rows nr-rows}]
                            [(new-page-crtl-button "<" nav)]))
          last-entries (when (> (- last-idx start-idx -1)
                                (* (- nr-buttons (count first-entries))
                                   nr-rows))
                         (let [next-start-idx (+ start-idx (* nr-rows (- nr-buttons 2)))
                               next-start-idx (if (> next-start-idx last-idx)
                                                last-idx next-start-idx)
                               nav {:render-crtl true
                                    :start-idx next-start-idx :last-idx last-idx
                                    :nr-buttons nr-buttons :nr-rows nr-rows}]
                           [(new-page-crtl-button ">" nav)]))
          nr-normal-buttons (Math.ceil (/ (- last-idx start-idx -1) nr-rows))
          delta-nr-buttons (- nr-buttons (+ nr-normal-buttons
                                            (count first-entries)
                                            (count last-entries)))
          nr-normal-buttons (if (< delta-nr-buttons 0)
                              (+ nr-normal-buttons delta-nr-buttons)
                              nr-normal-buttons)]
      (concat first-entries
              (create-page-crtl-button-groups nr-normal-buttons nr-rows
                                              start-idx)
              last-entries)))


  (defn render-table-controller
    "renders page control buttons within table with given dom-id-str
   start at start-idx. The number of rows controlled by each
   button is specified by argument nr-rows. The table shall
   defined a distinctive prototype-row where the number of
   buttons to be rendered is taken from. Refer also to render-table
   for more detailed information about table rendering."
    [table-id-str start-idx last-idx nr-rows]
    (let [table (dom/get-element table-id-str)
          table-body (gdom/getFirstElementChild table)
          prototype-row (get-element "prototype-row" table-body)
          nr-buttons (count (htmlcoll2array (. prototype-row -cells)))
          crtlButtons (create-page-crtl-buttons start-idx last-idx nr-buttons nr-rows)]
      (when-let [buttons (. table -crtlButtons)] (map #(. % (dispose))) buttons)
      (dorun (map #(set! (. % -table-controller) table-id-str) crtlButtons)) ; backward ref
      (set! (. table -crtlButtons) crtlButtons)
      (clear-table table-id-str)
      (render-table table-id-str (vector crtlButtons))
      table))



  (defn update-table-contoller-button-state
    "disables the button state of the controll
     button which belongs to the currently displayed
     content beginning at rendered-start-idx and
     enables all other buttons."
    [table-controller rendered-start-idx]
    (let [table (dom/get-element table-controller)
          buttons (. table -crtlButtons)]
      (dorun
       (map #(let [{:keys [start-idx nr-rows render-crtl]} (. % -evt-params)]
               (if (and (>= rendered-start-idx start-idx)
                        (< rendered-start-idx (+ start-idx nr-rows))
                        (not render-crtl))
                 (. % (setEnabled false))
                 (. % (setEnabled true)))) buttons))))


  ;(def a (update-table-contoller-button-state "search-result-controller" 15))
  ;(def b (first a))
  ;(. b -evt-params)


  (defn get-table-controller-reactor
    "creates event reactor for the controlling a
     search result table using the given table
     controller and a data-array with all content
     to be displayed."
    [table-controller rendered-table data-array]
    (dispatch/react-to
     #{table-controller}
     (fn [evt data]
       (let [start-idx (:start-idx data)
             last-idx (count data-array)
             first-row-idx (dec start-idx)
             last-row-idx (+ first-row-idx (:nr-rows data))
             last-row-idx (if (> last-row-idx last-idx) last-idx last-row-idx)]
         (loginfo (str "received page-crtl event: " (pr-str data)))
         (when (:render-crtl data)
           (render-table-controller table-controller
                                    start-idx
                                    (:last-idx data)
                                    (:nr-rows data)))
         (clear-table rendered-table)
         ;(println "first: " first-row-idx "last: " last-row-idx)
         (render-table rendered-table (subvec data-array first-row-idx last-row-idx))
         (update-table-contoller-button-state table-controller start-idx)
         ))))


  (defn init-search-result-table
    "creates a search result table object which controlls
     two DOM html tables, one for the search results to
     be rendered with the dom id string specified in
     table-content the other the search controll buttons
     for skipping to next or previous entries. Both tables
     shall provide one table row with id 'prototype' which
     is copied for each new rendered line within the content
     table. The data is given within the corresponding element
     as two dimensional vector. The outer or first index
     specifies the table rows while the innter or second index
     correspond to the result columns. The nr-rows specifies
     the number of search results which should be displayed in
     one page (rendering step). The functions also registers
     all event handlers for switching to the approriate result
     page selected by the user and returns an object which
     is used for later release."
    [table-controller table-content data nr-rows]
    (let [last-idx (count data)
          crtl-reactor (get-table-controller-reactor table-controller table-content data)
          table (render-table-controller table-controller 1 last-idx nr-rows)
          buttons (. table -crtlButtons)
          button (first buttons)]
      (dispatch/fire (. button -table-controller)
                     (. button -evt-params))
      {:crtl-reactor crtl-reactor :table-controller table-controller
       :table-content table-content}))


  (defn release-search-result-table
    "releases search result table objects and cleans the
     content and the controller table."
    [{:keys [crtl-reactor table-controller table-content]} result-table-obj]
    (dispatch/delete-reaction crtl-reactor)
    (when-let [buttons (. table-controller -crtlButtons)] (map #(. % (dispose))) buttons)
    (clear-table table-controller)
    (clear-table table-content))


  (comment

    (clear-table "search-result-table")
    (render-table "search-result-table"
                  [["Karl" "100km" "57%"]
                   ["Anton" "70km" "68%"]])


    (def x (create-page-crtl-button-groups 5 5))
    (def x (create-page-crtl-button-groups 5 5 26))

    (def y (create-page-crtl-buttons 1 1 5 5))
    (def y (create-page-crtl-buttons 1 6 5 5))
    (def y (create-page-crtl-buttons 1 10 5 5))
    (def y (create-page-crtl-buttons 1 11 5 5))
    (def y (create-page-crtl-buttons 1 15 5 5))
    (def y (create-page-crtl-buttons 1 16 5 5))
    (def y (create-page-crtl-buttons 1 20 5 5))
    (def y (create-page-crtl-buttons 1 21 5 5))
    (def y (create-page-crtl-buttons 1 25 5 5))

    (def y (create-page-crtl-buttons 1 26 5 5))
    (def y (create-page-crtl-buttons 2 5 5 5))

    (def y (create-page-crtl-buttons 2 25 5 5))

    (filter #(not-empty %) y)
    (render-table "search-result-controller" (vector x))
    (render-table "search-result-controller" (vector y))

    (map #(. % (dispose)) x)
    (map #(. % (dispose)) y)

    (clear-table "search-result-controller")

    (def x (render-table-controller "search-result-controller" 1 25 5))

    (def x (render-table-controller "search-result-controller" 6 38 5))

    (def reactor (get-table-controller-reactor
                  "search-result-controller"
                  "search-result-table"
                  (create-test-data)))

    (dispatch/delete-reaction reactor)


    (def result-table
      (init-search-result-table "search-result-controller"
                                "search-result-table" (create-test-data) 5))

    (release-search-result-table result-table)


    (def a [["Karl" "100km" "57%"]
              ["Anton" "70km" "68%"]])

    (defn create-test-data
      []
      (let [first-names ["Paul" "Lisa" "Andreas" "Paula" "Gert" "Gerda" "Patrick" "Sabine"
                      "Gustav" "Monika" "Olaf" "Andrea" "Ottmar" "Patricia" "Heiner"
                      "Anna" "Sebastian" "Gudrun" "Christoph" "Silke" "Max" "Sandy"]
            second-names ["Müller" "Schmidt" "Bauer" "Schuhmacher" "Stein" "Pfennig"
                       "Bäcker" "Schuster" "Bleichert" "Schulz" "Ludwig" "Mai"
                       "Röhl" "Richter" "Hofer" "Kling" "Hauser" "Kaindl" "Kiefer"]]
        (for [first-name first-names second-name second-names]
          [(str first-name " " second-name)
           (str (rand-int 100) "km")
           (str (rand-int 100) "%")])))


    (def a (create-test-data))

    (count a)
    (apply sorted-map (interleave (map #(second %) a)
                                  (map #(vector (first %) (nth % 2)) a)))

    (sort #(compare (first %1) (first %2)) a)

    (defn unitstr2num [string] (apply js/Number (re-seq #"-?[\d.]+" string)))
    (unitstr2num "123%")

    (def sort-by-name (partial sort #(compare (first %1) (first %2))))
    (def sort-by-dist (partial sort #(compare
                                      (unitstr2num (second %1))
                                      (unitstr2num (second %2)))))
    (def sort-by-match (partial sort #(compare
                                      (unitstr2num (nth %2 2))
                                      (unitstr2num (nth %1 2)))))

    (sort-by-name a)
    (sort-by-dist a)
    (sort-by-match a)

    (* 2 (js/Number "10km"))
    (map #(vector a) a)


    ) ; end of usage illustration

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

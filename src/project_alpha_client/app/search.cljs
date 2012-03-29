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
  (:use [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.utils :only [send-request get-element
                                               init-alpha-button]]))

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
          (let [cells (htmlcoll2array (gdom/getChildren row))
                first-cell (first cells)
                tag-name (. first-cell -tagName)]
            (when-not (or (= "prototype-row" (. row -id))
                          (= (string/upper-case tag-name) "TH"))
              (dorun (map #(when-let [obj (. % -rendered)] (. obj (dispose))) cells))
              (gdom/removeNode row)
              )))))

  (defn- function? [f] (= "function" (goog.typeOf f)))

  (defn render-table
    "Renders an html table with the given dom id string and
   an array of table rows where each table row is in turn
   an array of column elements. The html table is expected
   to have one prototype row which is cloned for each new
   row which is added to the array.
   The column elements must either be strings which are
   rendered directly or it possible to provide a render
   function which invoked with the enclosing cell as
   argument. In the later case the enclosing cell gets
   a reference to the rendered object within the field
   'rendered' which is used for later clean-up (function
   clear-table)."
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
              (cond
               (string? col-data) (set! (. col-elem -innerHTML) col-data)
               (function? col-data) (set! (. col-elem -rendered) (col-data col-elem))
               true (. col-data (render col-elem)) ; when-not string or function, render obj.
               )))
          (gdom/appendChild table-body new-row)))))


  (defn- new-page-crtl-button
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


  (defn- create-page-crtl-button-groups
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


  (defn- create-page-crtl-buttons
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


  (defn- render-table-controller
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



  (defn- update-table-contoller-button-state
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


  (defn- get-table-controller-reactor
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


  (defn render-table-button
    "renders a button inside a table with given
       label. Dispatches given event and data when
       the button is clicked. The parameter parent-
       element specifies the enclosing element where
       the button is rendered in, here the table
       cell."
    [label event evt-data parent-element]
    (let [button (goog.ui.Button. label (FlatButtonRenderer/getInstance))]
      (events/listen button "action"
                     #(dispatch/fire event evt-data))
      (. button (render parent-element))
      (set! (. button -evt-params) evt-data)
      button
      ))


  (defn- init-table-sort-buttons
    [table-id-str event]
    (let [table (dom/get-element table-id-str)
          table-body (gdom/getFirstElementChild table)
          header-row (get-element "header-row" table-body)
          cells (htmlcoll2array (. header-row -cells))
          buttons (map
                   #(when (re-seq #"^sort" (. % -id))
                      (let [txt (. % -innerText)]
                        (set! (. % -innerText) "")
                        (render-table-button txt event (. % -id) %)))
                   cells)
          buttons (doall (filter identity buttons))]
      (set! (. table -sortButtons) buttons)
      buttons
      ))


  (defn- release-table-sort-buttons
    [table-id-str]
    (let [table (dom/get-element table-id-str)
          table-body (gdom/getFirstElementChild table)
          header-row (get-element "header-row" table-body)
          cells (htmlcoll2array (. header-row -cells))
          header-txt (doall
                      (map
                       #(when-let [child-elem (. % -firstChild)]
                          (when (= "DIV" (. child-elem -tagName))
                            (. child-elem -innerText)))
                       cells))]
      (dorun (map #(. % (dispose)) (. table -sortButtons))) ; remove sort buttons
      (dorun (map #(set! (. %1 -innerText) %2) cells header-txt)) ; restore orig. header
      ))


  (defn- get-sort-reactor
    "instantiates an event handler for sorting a search
     result table (sort-reactor). This function is used
     within init-sortable-search-result-table."
    [sort-buttons search-result-table-atom
     table-controller table-content data nr-rows evt-sort-function-hashes]
    (dispatch/react-to
     #{:sort-search-results}
     (fn [evt evt-data]
       (loginfo (str "sort-by-xxx clicked: " evt-data))
       (dorun
        (map #(if (= (. % -evt-params) evt-data)
                (. % (setEnabled false))
                (. % (setEnabled true))) sort-buttons))
       (release-search-result-table @search-result-table-atom)
       (let [sorted-data ((evt-sort-function-hashes evt-data) data)]
         (reset! search-result-table-atom
                 (init-search-result-table table-controller
                                           table-content sorted-data nr-rows)))
       )))


  (defn init-sortable-search-result-table
    "creates a sortable search result table object which
     controlls two DOM html tables, one for the search results
     to be rendered with the dom id string specified in
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
    [table-controller table-content data nr-rows evt-sort-function-hashes]
    (let [search-result-table (init-search-result-table table-controller
                                                        table-content data nr-rows)
          search-result-table-atom (atom search-result-table)
          sort-buttons (init-table-sort-buttons table-content :sort-search-results)
          sort-reactor (get-sort-reactor
                        sort-buttons search-result-table-atom
                        table-controller
                        table-content
                        data
                        nr-rows
                        evt-sort-function-hashes)]
      (dispatch/fire :sort-search-results (first (keys evt-sort-function-hashes)))
      {:search-result-table-atom search-result-table-atom
       :sort-buttons sort-buttons
       :sort-reactor sort-reactor}))


  (defn release-sortable-search-result-table
    "releases sortable search result table objects and cleans the
     content and the controller table."
    [{:keys [search-result-table-atom sort-buttons sort-reactor]} sortable-result-table-obj]
    (release-table-sort-buttons (@search-result-table-atom :table-content))
    (dispatch/delete-reaction sort-reactor)
    (release-search-result-table @search-result-table-atom)
    )


  (comment

    (defn unitstr2num [string] (apply js/Number (re-seq #"-?[\d.]+" string)))
    (defn german-date-str-to-ms
      [datestr]
      (let [[day month year] (map js/Number (. datestr (split ".")))]
        (. (js/Date. year month day) (getTime))))

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

    (release-sortable-search-result-table sortable-result-table)

    )


  (comment "usage illustration"

    (def table (dom/get-element "search-result-table"))
    (def table-body (gdom/getFirstElementChild table))
    (def header-row (get-element "header-row" table-body))
    (def cells (htmlcoll2array (. header-row -cells)))
    (def first-cell (first cells))
    (. first-cell -id)

    (def y (first x))
    (. y -evt-params)

    (def x (init-table-sort-buttons "search-result-table" :sort-search-results))
    (release-table-sort-buttons "search-result-table")

    (def sort-reactor (dispatch/react-to
                       #{:sort-search-results}
                       (fn [evt data]
                         (loginfo (str "sort-by-xxx clicked: " data))
                         (dorun
                          (map #(if (= (. % -evt-params) data)
                                  (. % (setEnabled false))
                                  (. % (setEnabled true))) x))
                         )))

    (dispatch/delete-reaction sort-reactor)

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
                                "search-result-table" (create-test-data) 10))

    (release-search-result-table result-table)


    (def a [["Karl" "100km" "57%"]
              ["Anton" "70km" "68%"]])

    (def details-reactor (dispatch/react-to
                          #{:show-user-details}
                          (fn [evt data]
                            (loginfo (str "detail button pressed for user id: " data)))))


    (defn create-test-data
      []
      (let [first-names ["Paul" "Lisa" "Andreas" "Paula" "Gert" "Gerda" "Patrick" "Sabine"
                         "Gustav" "Monika" "Olaf" "Andrea" "Ottmar" "Patricia" "Heiner"
                         "Anna" "Sebastian" "Gudrun" "Christoph" "Silke" "Max" "Sandy"]
            second-names ["Mueller" "Schmidt" "Bauer" "Schuhmacher" "Stein" "Pfennig"
                          "Baecker" "Schuster" "Bleichert" "Schulz" "Ludwig" "Mai"
                          "Roehl" "Richter" "Hofer" "Kling" "Hauser" "Kaindl" "Kiefer"]
            name-array (for [first-name first-names second-name second-names]
                         (str first-name " " second-name))
            dates ["12.02.2012" "11.11.2011" "10.09.2011" "01.08.2011" "27.09.2011" "31.12.2011" "01.05.2011"]]
        (doall
         (map #(vector %3 %1 (str (rand-int 100) "km")
                       (str (rand-int 100) "%")
                       (partial render-table-button (str "id-" %2) :show-user-details (str %2)))
              name-array (iterate inc 1) (flatten (repeat dates))))))


    (def a (create-test-data))





    (count a)
    (apply sorted-map (interleave (map #(second %) a)
                                  (map #(vector (first %) (nth % 2)) a)))

    (sort #(compare (first %1) (first %2)) a)

    (defn unitstr2num [string] (apply js/Number (re-seq #"-?[\d.]+" string)))

    (defn german-date-str-to-ms
      [datestr]
      (let [[day month year] (map js/Number (. datestr (split ".")))]
        (. (js/Date. year month day) (getTime))))


    (unitstr2num "123%")
    (german-date-str-to-ms "27.10.2011")

    (def sort-by-date (partial sort #(compare
                                       (german-date-str-to-ms (first %1))
                                       (german-date-str-to-ms (first %2)))))
    (def sort-by-name (partial sort #(compare (second %1) (second %2))))
    (def sort-by-dist (partial sort #(compare
                                      (unitstr2num (nth %1 2))
                                      (unitstr2num (nth %2 2)))))
    (def sort-by-match (partial sort #(compare
                                      (unitstr2num (nth %2 3))
                                      (unitstr2num (nth %1 3)))))
    (def b (sort-by-name a))
    (def b (sort-by-dist a))
    (def b (sort-by-match a))

    (def result-table
      (init-search-result-table "search-result-controller"
                                "search-result-table" b 10))

    (release-search-result-table result-table)

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

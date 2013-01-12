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
            [project-alpha-client.app.user-details-dialog :as user-details]
            [clojure.browser.dom :as dom]
            [clojure.string :as string]
            [goog.dom :as gdom]
            [goog.style :as style]
            [goog.events :as events]
            [goog.ui.Button :as Button]
            [goog.ui.ButtonRenderer :as ButtonRenderer]
            [goog.ui.FlatButtonRenderer :as FlatButtonRenderer]
            [goog.ui.TabPane :as TabPane]
            [goog.ui.Dialog :as Dialog]
            [goog.Timer :as timer]
            [project-alpha-client.lib.dispatch :as dispatch])
  (:use [project-alpha-client.lib.table-controller
         :only [init-sortable-search-result-table
                release-sortable-search-result-table
                render-table-button
                create-test-data]]
        [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.utils
         :only [get-modal-dialog open-modal-dialog
                get-element init-alpha-button]]
        [project-alpha-client.lib.ajax :only [send-request]]))


;;; the profile page (client side equivalent to index.html)
(def search-pane (dom/get-element "search-pane"))


(when search-pane

  ;; the result table objects are initialized when firstly clicked
  ;; too high initial delay
  (def result-table-atom (atom nil))
  (def favorite-table-atom (atom nil))
  (def banned-table-atom (atom nil))
  (def fav-user-ids-atom (atom nil))
  (def fav-user-data-atom (atom nil))
  (def banned-user-ids-atom (atom nil))
  (def banned-user-data-atom (atom nil))

  ;; --- sortable search result table ---

  (defn unitstr2num [string] (apply js/Number (re-seq #"-?[\d.]+" string)))
  (defn german-date-str-to-ms
    [datestr]
    (let [[day month year] (map js/Number (. datestr (split ".")))]
      (. (js/Date. year month day) (getTime))))


  (defn- render-table
    [table-id controller-id data]
    (init-sortable-search-result-table controller-id table-id data 10
     {"sort-by-date" (partial sort #(compare
                                     (german-date-str-to-ms (first %2))
                                     (german-date-str-to-ms (first %1))))
      "sort-by-name" (partial sort #(compare (second %1) (second %2)))
      "sort-by-dist" (partial sort #(compare
                                     (unitstr2num (nth %1 2))
                                     (unitstr2num (nth %2 2))))
      "sort-by-match" (partial sort #(compare
                                      (unitstr2num (nth %2 3))
                                      (unitstr2num (nth %1 3))))}))




  (defn- gen-table-data
    "transforms ajax response data in input for table-controller functions"
    [data]
    (let [details-button-txt (goog.dom.getTextContent
                              (get-element "show-details-button-txt" search-pane))]
        (doall
         (map #(let [id (first %)
                     name ((second %) "name")
                     created-at ((second %) "created_at")
                     match-correlation (- 100 ((second %) "match_variance"))
                     distance ((second %) "distance")]
                 (vector created-at  name (str distance "km")
                         (str match-correlation "%")
                         (partial render-table-button
                                  details-button-txt ;; (str "id-" id)
                                  :show-user-details (str id))))
              data))))


  (defn- flush-profile
    "ensures that profile data is synchronized and checked on server.
     post the request to flush the user's profile 100ms delayed to
     give pending post requests send from the profile's pane a chance
     to complete.
     The 'flush-profile' post request answers with a list of non formed
     profile's data which can be be used to direct users what is
     specifically missing."
    [callback]
    (timer/callOnce
     #(send-request "/flush-profile"
                    ""
                    (fn [ajax-evt]
                      (let [resp (. (. ajax-evt -target) (getResponseText))
                            missing (json/parse resp)]
                        (loginfo (str "profile flushed and checked, result:" resp))
                        (if (empty? missing)
                          (callback)
                          (do
                            (style/showElement (dom/get-element
                                                "search_profile_incomplete") true)
                            (style/showElement (dom/get-element
                                            "search_request_progress") false)))))
                    "POST")
     100))


  (defn- request-result-pane [force-update]
    "retrieves all matching users and updates table view controller"
    (when force-update
      (when @result-table-atom
        (release-sortable-search-result-table @result-table-atom)
        (reset! result-table-atom nil)))
    (when-not @result-table-atom
      (style/showElement (dom/get-element
                          "search_request_progress") true)
      (flush-profile
       #(send-request "/user-matches"
                      {}
                      (fn [ajax-evt]
                        (let [resp (. (. ajax-evt -target) (getResponseText))
                              resp (json/parse resp)]
                          (if (resp "data")
                            (do (reset! result-table-atom
                                        (render-table
                                         "search-result-table"
                                         "search-result-controller"
                                         (gen-table-data (resp "data"))))
                                (style/showElement (dom/get-element
                                                    "search_profile_incomplete") false))
                            (js/alert "Transmission Error!")))
                        (style/showElement (dom/get-element
                                            "search_request_progress") false))))))


  (defn- request-result-pane-test [force-update]
    "updates table view controller with some test data
     (development purposes)"
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
                  (take 30 (create-test-data))))
         (style/showElement (dom/get-element
                             "search_request_progress") false))
       10)))


  (comment
    "here we are going to connect the client request and the servers
     repsone ..."
    (send-request "/user-matches"
                  { "key1" "data1"}
                  (fn [ajax-evt]
                    (let [resp (. (. ajax-evt -target) (getResponseText))]
                      (loginfo resp)
                      (def resp resp)))
                  "GET")

    (gen-table-data (json/parse resp))

    )


  (defn- request-favorite-pane-test [force-update]
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


  (defn request-favorite-pane [force-update]
    "retrieves all matching users and updates table view controller"
    (when force-update
      (when @favorite-table-atom
        (release-sortable-search-result-table @favorite-table-atom)
        (reset! favorite-table-atom nil)))
    (when-not @favorite-table-atom
      (style/showElement (dom/get-element
                          "search_request_progress") true)
      (send-request "/user-fav-user-ids"
                    {}
                    (fn [ajax-evt]
                      (let [resp (. (. ajax-evt -target) (getResponseText))]
                        (reset! fav-user-ids-atom (set (json/parse resp))))))
      (send-request "/user-favorites"
                    {}
                    (fn [ajax-evt]
                      (let [resp (. (. ajax-evt -target) (getResponseText))
                            resp (json/parse resp)
                            no-fav-pane (dom/get-element "search_no_favorites")]
                        (if (resp "data")
                          (do
                            (reset! fav-user-data-atom (resp "data"))
                            (reset! favorite-table-atom
                                    (render-table
                                     "favorite-table"
                                     "favorite-controller"
                                     (gen-table-data (resp "data")))))
                          (reset! @fav-user-data-atom nil))
                        (style/showElement (dom/get-element
                                            "search_request_progress") false)
                        (if (empty? @fav-user-data-atom)
                          (style/showElement no-fav-pane true)
                          (style/showElement no-fav-pane false))))
                    )))

  (defn request-banned-pane [force-update]
    "retrieves all filtered out and updates table view controller"
    (when force-update
      (when @banned-table-atom
        (release-sortable-search-result-table @banned-table-atom)
        (reset! banned-table-atom nil)))
    (when-not @banned-table-atom
      (style/showElement (dom/get-element
                          "search_request_progress") true)
      (send-request "/user-banned-user-ids"
                    {}
                    (fn [ajax-evt]
                      (let [resp (. (. ajax-evt -target) (getResponseText))]
                        (reset! banned-user-ids-atom (set (json/parse resp))))))
      (send-request "/user-banned"
                    {}
                    (fn [ajax-evt]
                      (let [resp (. (. ajax-evt -target) (getResponseText))
                            resp (json/parse resp)
                            no-banned-pane (dom/get-element "search_no_banned")]
                        (if (resp "data")
                          (do
                            (reset! banned-user-data-atom (resp "data"))
                            (reset! banned-table-atom
                                    (render-table
                                     "banned-table"
                                     "banned-controller"
                                     (gen-table-data (resp "data")))))
                          (reset! @banned-user-data-atom nil))
                        (style/showElement (dom/get-element
                                            "search_request_progress") false)
                        (if (empty? @banned-user-data-atom)
                          (style/showElement no-banned-pane true)
                          (style/showElement no-banned-pane false))))
                    )))

  ;; --- @todo: update favorite list exclusively on client side ---

  (comment

    (defn- add-usr-id-to-fav-pane [id]
      (swap! fav-user-ids-atom conj data)
      (comment ...)
      )

    (defn- del-usr-id-to-fav-pane [id]
      (swap! fav-user-ids-atom disj data)
      (comment ...)
      )
    )


  ;; --- sortable search result table event handling ---


  (def ^{:private true
         :doc "event handler for opening the details dialog for specified user"}
    user-details-reactor
    (dispatch/react-to
     #{:show-user-details}
     (fn [evt data]
       (let [id (js/Number data)]
         (loginfo (str "detail button pressed for user id: " id))
                                        ;(user-details/render-sample-user)
         (user-details/render-user-with-id id)
         (user-details/open-dialog data
                                   :is-in-fav-list (contains? @fav-user-ids-atom id)
                                   :is-in-banned-list (contains? @banned-user-ids-atom id))))))


  (def ^{:private true
         :doc "event handler for adding user to favorite list"}
    add-fav-user-reactor
    (dispatch/react-to
     #{:add-user-to-fav}
     (fn [evt data]
       (let [data (js/Number data)]
         (loginfo (pr-str evt data))
         (user-details/open-dialog data :is-in-fav-list true)
         (send-request "/add-fav-user"
                       (json/generate {:match_id data})
                       (fn [e] (request-favorite-pane true))
                       "POST")))))


  (def ^{:private true
         :doc "event handler for removing user from favorite list"}
    del-fav-user-reactor
    (dispatch/react-to
     #{:rm-user-from-fav}
     (fn [evt data]
       (let [data (js/Number data)]
         (loginfo (pr-str evt data))
         (request-favorite-pane true)
         (user-details/open-dialog data :is-in-fav-list false)
         (send-request "/del-fav-user"
                       (json/generate {:match_id data})
                       (fn [e] (request-favorite-pane true))
                       "POST")))))


  (def ^{:private true
         :doc "event handler for adding user to banned list"}
    add-banned-user-reactor
    (dispatch/react-to
     #{:add-user-to-banned}
     (fn [evt data]
       (let [data (js/Number data)]
         (loginfo (pr-str evt data))
         (user-details/open-dialog data :is-in-banned-list true)
         (send-request "/add-banned-user"
                       (json/generate {:match_id data})
                       (fn [e] (request-banned-pane true)
                         (request-result-pane true))
                       "POST")))))


  (def ^{:private true
         :doc "event handler for removing user from banned list"}
    del-banned-user-reactor
    (dispatch/react-to
     #{:rm-user-from-banned}
     (fn [evt data]
       (let [data (js/Number data)]
         (loginfo (pr-str evt data))
         (request-favorite-pane true)
         (user-details/open-dialog data :is-in-banned-list false)
         (send-request "/del-banned-user"
                       (json/generate {:match_id data})
                       (fn [e] (request-banned-pane true)
                         (request-result-pane true))
                       "POST")))))



  ; --- the tab pane ---

  (def tabpane (goog.ui.TabPane. (get-element "tabpane-search" search-pane)))
  (. tabpane (addPage (TabPane/TabPage. (get-element "search-results" search-pane))))
  (. tabpane (addPage (TabPane/TabPage. (get-element "favorites" search-pane))))
  (. tabpane (addPage (TabPane/TabPage. (get-element "banned" search-pane))))
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
      "banned" (request-banned-pane false)
      "search-results" (request-result-pane false)
      "search-setup" (loginfo "search-setup")))


  (def search-tab-changed-reactor
    (dispatch/react-to
     #{:search-tab-changed}
     (fn [evt data]
       (update-tab-panes (get-page-id-str data)))))





  (comment use the method below to release resources

    (dispatch/delete-reaction search-tab-changed-reactor)
    (release-sortable-search-result-table @result-table-atom)
    (reset! result-table-atom nil)

    (user-details/open-dialog 100 :is-in-fav-list true)
    (user-details/render-sample-user)

    ) ; end of comment



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
      (loginfo "search page enabled")
      ;; trigger initial pane changed event
      (dispatch/fire :search-tab-changed (. tabpane (getSelectedIndex))))
    (do
      (pages/reload-url "/search.html")
      (loginfo "search page reloaded"))))


(defn- disable-search-page
  "hides the search-page, activates the search"
  []
  (when search-pane
    (style/showElement search-pane false)
    (loginfo "search page disabled")))

;;;
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; functions for the status page
;;;
;;; 2011-11-23, Otto Linnemann

(ns project-alpha-client.app.status
  (:require [project-alpha-client.lib.pages :as pages]
            [project-alpha-client.app.nav :as nav]
            [project-alpha-client.lib.json :as json]
            [project-alpha-client.lib.editor :as editor]
            [clojure.browser.dom :as dom]
            [goog.style :as style]
            [goog.events :as events]
            [goog.ui.Button :as Button]
            [goog.ui.TabPane :as TabPane]
            [project-alpha-client.lib.dispatch :as dispatch])
  (:use [project-alpha-client.lib.utils :only [get-button-group-value
                                               set-button-group-value
                                               get-element
                                               get-modal-dialog
                                               open-modal-dialog]]
        [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.ajax :only [send-request]]
        [project-alpha-client.lib.table-controller
         :only [init-sortable-search-result-table
                release-sortable-search-result-table
                render-table-button
                create-test-data]]))

;;; the profile page (client side equivalent to index.html)
(def status-pane (dom/get-element "status-pane"))

(when status-pane


  ; -- new messages table ---

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
      "sort-by-name" (partial sort #(compare (second %1) (second %2)))}))


  (defn- get-status-msg-text
    [dom-id-str]
    (goog.dom.getTextContent (get-element dom-id-str status-pane))
    )

  (defn- gen-table-data
    "transforms ajax response data in input for table-controller functions"
    [data]
    (doall
     (map #(let [id (first %)
                 name ((second %) "from_user_name")
                 created-at ((second %) "creation_date")
                 message ((second %) "text")]
             (vector created-at
                     (partial render-table-button name :show-user-details (str id))
                     message
                     (partial render-table-button
                              (get-status-msg-text "showall-user-msg")
                              :showall-user-msg (str id))
                     (partial render-table-button
                              (get-status-msg-text "reply-user-msg")
                              :send-msg-to-user (str id))))
          data)))


  ;; result table objects
  (def new-messages-table-atom (atom nil))
  (def read-messages-table-atom (atom nil))

  (defn- request-new-messages []
    "retrieves new messages from server"
    (send-request "/unread-messages"
                  {}
                  (fn [ajax-evt]
                    (let [resp (. (. ajax-evt -target) (getResponseText))
                          resp (json/parse resp)]
                      (style/showElement
                       (get-element "msg_request_progress" status-pane) true)
                      (when @new-messages-table-atom
                        (release-sortable-search-result-table @new-messages-table-atom))
                      (def x resp)
                      (reset! new-messages-table-atom
                              (render-table
                               "new-messages-table"
                               "new-messages-controller"
                               (gen-table-data resp)))
                      (style/showElement
                       (get-element "msg_request_progress" status-pane) false)
                      ))))


  (defn- request-read-messages []
    "retrieves read messages from server"
    (send-request "/read-messages"
                  {}
                  (fn [ajax-evt]
                    (let [resp (. (. ajax-evt -target) (getResponseText))
                          resp (json/parse resp)]
                      (style/showElement
                       (get-element "msg_request_progress" status-pane) true)
                      (when @read-messages-table-atom
                        (release-sortable-search-result-table @read-messages-table-atom))
                      (reset! read-messages-table-atom
                              (render-table
                               "read-messages-table"
                               "read-messages-controller"
                               (gen-table-data resp)))
                      (style/showElement
                       (get-element "msg_request_progress" status-pane) false)
                      ))))


  (request-new-messages)
  (request-read-messages)

  (comment

    (def test-data
      {
       "6" {"from_user_name" "Otto" "creation_date" "1.11.2012" "text" "message 6" "status" "unanswered"}
       "5061" {"from_user_name" "Sabinchen" "creation_date" "2.11.2012" "text" "message 5061"}
       }
      )

    (reset! new-messages-table-atom
            (render-table
             "new-messages-table"
             "new-messages-controller"
             (gen-table-data test-data )))
    )


  ;; instantiate the compose message dialog
  (let [[dialog ok-button cancel-button]
        (get-modal-dialog
         :panel-id "compose-msg-dialog"
         :title-id "compose-msg-dialog-title"
         :ok-button-id "confirm-compose-msg"
         :cancel-button-id "cancel-compose-msg"
         :dispatched-event :msg-compose-dialog-confirmed
         :keep-open true
         )]
    (style/setStyle (. dialog (getElement)) "z-index", "4")
    (def msg-compose-dialog dialog)
    (def confirm-msg-compose-button ok-button)
    (def cancel-msg-compose-button cancel-button))


  (def editor (editor/create "cmp-msg-editor" "cmp-msg-toolbar"))
  ;; (open-modal-dialog msg-compose-dialog)
  (. editor (setHtml false "" true))
  (. editor (makeEditable))


  (defn- set-ref-mail-html-txt
    "renders user data"
    [dialog html-txt]
    (let [html-txt-elem (get-element "ref-msgs" (. dialog (getContentElement)))]
      (set! (. html-txt-elem -innerHTML) html-txt)
      ))


  (defn- set-compose-enabled-state
    "The same dialog pane is used for composing a new message where
     the current message stream is rendered at the bottom of the
     dialog pane and for just showing this message stream. In the
     latter case the editor and the send/abort buttons are not
     rendered. This function is used to switch between both states."
    [enabled]
    (let [elem (. msg-compose-dialog (getContentElement))]
      (style/showElement
       (get-element "cmp-msg-toolbar" elem)
       enabled)
      (style/showElement
       (get-element "cmp-msg-editor" elem)
       enabled)
      (style/showElement
       (get-element "buttons" elem)
       enabled)))


  (defn- render-msg-stream-html
    "generates the html stream for the array of the
     message data stream received by
     render-communication-stream-with"
    [msg-array]
    (reduce #(str %1 ("text" %2) "<br />") "" msg-array))


  (defn- render-communication-stream-with
    "Compose a new message for user with given id. Function retrieves all
     previous messages with ajax request before opening the compose message
     dialog."
    [id msg-title]
    (style/showElement (get-element "compose_request_progress") true)
    (open-modal-dialog msg-compose-dialog)
    (send-request (str "/correspondence/" id)
                  ""
                  (fn [ajax-evt]
                    (let [resp (. (. ajax-evt -target) (getResponseText))
                          user-data (json/parse resp)
                          header (first user-data)
                          receiver-name ("to-name" header)
                          msg-array (second user-data)
                          msg-title (str msg-title receiver-name)]
                      (set! (. msg-compose-dialog -comm-stream) user-data)
                      (when msg-array
                        (set-ref-mail-html-txt msg-compose-dialog
                                               (render-msg-stream-html msg-array))
                        (. msg-compose-dialog (setTitle msg-title))
                        (style/showElement (get-element "compose_request_progress") false)
                        )
                      (loginfo (str "answer received: " resp))
                      (loginfo receiver-name)
                      )))
                  "GET")

  ;(render-communication-stream-with 5061 "Nachricht senden an: ")


  (defn open-compose-msg-dialog
    "opens a new compose message dialog with the addresse and a table
     of all previous messages as arguments."
    [id]
    (let [msg-title (get-element "compose-msg-dialog-title" status-pane)
          msg-title (goog.dom.getTextContent msg-title)]
      (set-compose-enabled-state true)
      (. editor (setHtml false "" true))
      (render-communication-stream-with id msg-title)
      )
    )


  (defn open-show-msg-dialog
    "opens a new compose message dialog with the addresse and a table
     of all previous messages as arguments."
    [id]
    (let [msg-title (get-element "show-msg-dialog-title" status-pane)
          msg-title (goog.dom.getTextContent msg-title)]
      (set-compose-enabled-state false)
      (render-communication-stream-with id msg-title)
      )
    )


  (comment usage-illustration
           (open-compose-msg-dialog 5061)
           (open-compose-msg-dialog "5061")

           (open-show-msg-dialog 5061)
           )


  (defn- post-new-message
    "transfer data for user with given id via ajax post request.
     The message data is fetched from the message dialog"
    []
    (let [comm-stream (. msg-compose-dialog -comm-stream)
          recv-id ("to-id" (first comm-stream))
          msg-txt (. editor (getCleanContents))]
      (style/showElement (get-element "compose_request_progress") true)
      (send-request "/new-message"
                    (json/generate {:recv-id recv-id :msg-txt msg-txt})
                    (fn [ajax-evt]
                      (style/showElement (get-element "compose_request_progress") false)
                      (let [resp (. (. ajax-evt -target) (getResponseText))]
                        (loginfo resp)
                        (if (= resp "OK")
                          (. msg-compose-dialog (setVisible false))
                          (js/alert (. (get-element
                                        "compose-msg-dialog-transmission-error"
                                        status-pane) -textContent)))))
                    "POST")))

  ; (post-new-message)

  (def ^{:private true
         :doc "event handler for showing communication stream with user"}
    show-all-msg-with-user-reactor
    (dispatch/react-to
     #{:showall-user-msg}
     (fn [evt data]
       (open-show-msg-dialog data)
       (loginfo (pr-str evt data)))))


  (def ^{:private true
         :doc "event handler for triggering compose message dialog"}
    send-msg-to-user-reactor
    (dispatch/react-to
     #{:send-msg-to-user}
     (fn [evt data]
       (open-compose-msg-dialog data)
       (loginfo (pr-str evt data)))))


  (def ^{:private true
         :doc "event handler for triggering ajax post for sending new msg."}
    msg-compose-dialog-confirmend-reactor
    (dispatch/react-to
     #{:msg-compose-dialog-confirmed}
     (fn [evt data]
       (post-new-message)
       (loginfo (pr-str evt data)))))



  ; --- receive and sent messages tab pane ---
  (def tabpane (goog.ui.TabPane. (get-element "msg-tab-pane" status-pane)))
  (. tabpane (addPage (TabPane/TabPage. (get-element "page1" status-pane))))
  (. tabpane (addPage (TabPane/TabPage. (get-element "page2" status-pane))))
  (. tabpane (addPage (TabPane/TabPage. (get-element "page3" status-pane))))

  ) ; (when status-pane)

(def site-enabled-reactor (dispatch/react-to
                           #{:page-switched}
                           (fn [evt data]
                             (if (= (:to data) :status)
                               (enable-status-page)
                               (disable-status-page)))))

(defn- enable-status-page
  "shows the status-page"
  []
  (if status-pane
    (do
      (style/setOpacity status-pane 1) ;; important for first load only
      (style/showElement status-pane true)
      (nav/enable-nav-pane)
      (loginfo "status page enabled"))
    (do
      (pages/reload-url "/status.html")
      (loginfo "status page reloaded"))))


(defn- disable-status-page
  "hides the status-page, activates the status"
  []
  (when status-pane
    (style/showElement status-pane false)
    (loginfo "status page disabled")))

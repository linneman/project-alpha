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
            [goog.Timer :as timer]
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
                create-test-data]]
        [project-alpha-client.lib.auth :only [base64-sha1]]))

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

  (defn- gen-received-msg-table-data
    "transforms ajax response of receveived message data in input
     for table-controller functions"
    [data]
    (doall
     (map #(let [id (first %)
                 name ((second %) "from_user_name")
                 created-at ((second %) "creation_date")
                 message ((second %) "text")
                 answered ((second %) "answered")]
             (vector created-at
                     (partial render-table-button name :show-user-details (str id))
                     message
                     (partial render-table-button
                              (get-status-msg-text "showall-user-msg")
                              :showall-user-msg (str id))
                     (if answered
                       " "
                       (partial render-table-button
                                (get-status-msg-text "reply-user-msg")
                                :send-msg-to-user (str id)))))
          data)))

  (defn- gen-send-msg-table-data
    "transforms ajax response of receveived message data in input
     for table-controller functions"
    [data]
    (doall
     (map #(let [id (first %)
                 name ((second %) "to_user_name")
                 created-at ((second %) "creation_date")
                 message ((second %) "text")]
             (vector created-at
                     (partial render-table-button name :show-user-details (str id))
                     message
                     (partial render-table-button
                              (get-status-msg-text "showall-user-msg")
                              :showall-user-msg (str id))))
          data)))


  ;; result table objects
  (def new-messages-table-atom (atom nil))
  (def new-messages-sha1-atom (atom nil))
  (def read-messages-table-atom (atom nil))
  (def unanswered-messages-table-atom (atom nil))

  (defn- request-new-messages []
    "retrieves new messages from server"
    (send-request "/unread-messages"
                  {}
                  (fn [ajax-evt]
                    (let [resp-txt (. (. ajax-evt -target) (getResponseText))
                          resp (json/parse resp-txt)]
                      (reset! new-messages-sha1-atom (base64-sha1 resp-txt))
                      (style/showElement
                       (get-element "msg_request_progress" status-pane) true)
                      (when @new-messages-table-atom
                        (release-sortable-search-result-table @new-messages-table-atom))
                      (reset! new-messages-table-atom
                              (render-table
                               "new-messages-table"
                               "new-messages-controller"
                               (gen-received-msg-table-data resp)))
                      (style/showElement
                       (get-element "msg_request_progress" status-pane) false)
                      (style/showElement
                       (get-element "msg_no_new_messages" status-pane) (empty? resp))))))


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
                               (gen-received-msg-table-data resp)))
                      (style/showElement
                       (get-element "msg_request_progress" status-pane) false)
                      (style/showElement
                       (get-element "msg_no_read_messages" status-pane) (empty? resp))
                      ))))


  (defn- request-unanswered-messages []
    "retrieves that have not been answered yet from server"
    (send-request "/unanswered-messages"
                  {}
                  (fn [ajax-evt]
                    (let [resp (. (. ajax-evt -target) (getResponseText))
                          resp (json/parse resp)]
                      (style/showElement
                       (get-element "msg_request_progress" status-pane) true)
                      (when @unanswered-messages-table-atom
                        (release-sortable-search-result-table @unanswered-messages-table-atom))
                      (reset! unanswered-messages-table-atom
                              (render-table
                               "unanswered-messages-table"
                               "unanswered-messages-controller"
                               (gen-send-msg-table-data resp)))
                      (style/showElement
                       (get-element "msg_request_progress" status-pane) false)
                      (style/showElement
                       (get-element "msg_no_unanswered_messages" status-pane) (empty? resp))
                      ))))


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
    [dialog html-txt elem]
    (let [html-txt-elem (get-element elem (. dialog (getContentElement)))]
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
       enabled)
      (style/showElement
       (get-element "ref-msgs-half" elem)
       enabled)
      (style/showElement
       (get-element "ref-msgs-full" elem)
       (not enabled))))


  (defn- render-msg-stream-html
    "generates the html stream for the array of the
     message data stream received by
     render-communication-stream-with"
    [msg-array id-name-hash]
    (reduce #(str %1
                  "<small>"
                  ("creation_date" %2) ", "
                  (id-name-hash ("from_user_id" %2))
                  "<br /><br /></small>"
                  ("text" %2) "<br /><hr />") "" msg-array))


  (defn- render-communication-stream-with
    "Compose a new message for user with given id. Function retrieves all
     previous messages with ajax request before opening the compose message
     dialog."
    [id msg-title comm-stream-elem]
    (style/showElement (get-element "compose_request_progress") true)
    (open-modal-dialog msg-compose-dialog)
    (send-request (str "/correspondence/" id)
                  ""
                  (fn [ajax-evt]
                    (let [respt (. (. ajax-evt -target) (getResponseText))
                          user-data (json/parse respt)
                          header (first user-data)
                          receiver-id ("to-id" header)
                          receiver-name ("to-name" header)
                          sender-id ("from-id" header)
                          sender-name ("from-name" header)
                          msg-array (second user-data)
                          msg-title (str msg-title receiver-name)]
                      (set! (. msg-compose-dialog -comm-stream) user-data)
                      (when msg-array
                        (set-ref-mail-html-txt msg-compose-dialog
                                               (render-msg-stream-html msg-array
                                                                       {receiver-id receiver-name
                                                                        sender-id sender-name})
                                               comm-stream-elem)
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
      (render-communication-stream-with id msg-title "ref-msgs-half")
      )
    )


  (defn open-show-msg-dialog
    "opens a new compose message dialog with the addresse and a table
     of all previous messages as arguments."
    [id]
    (let [msg-title (get-element "show-msg-dialog-title" status-pane)
          msg-title (goog.dom.getTextContent msg-title)]
      (set-compose-enabled-state false)
      (render-communication-stream-with id msg-title "ref-msgs-full")
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


  (def
    ^{:private true
      :doc "timer for updating new messages,
            see watched-correspondence-reactor"}
    update-new-messages-atom (atom nil))

  (def
    ^{:private true
      :doc "timer for updating messages, that
            have not been answered yet.
            see send-new-message-reactor"}
    update-unanswered-messages-atom (atom nil))


  (def
    ^{:private true
      :doc "whenever the correspondence with somebody has been watched
            either by clicking the corresponding details or by clicking
            the answer button, a 10 seconds timer is started which
            triggers updating of new messages. The timer is used to
            give the user a chance to hit the answer button. Without the
            timer corresponding correspondece would immediately appear
            within another pane which might distract the user."}
    watched-correspondence-reactor
    (dispatch/react-to
     #{:showall-user-msg :send-msg-to-user}
     (fn [evt data]
       (when @update-new-messages-atom ; clear any previously defined timer
         (timer/clear @update-new-messages-atom))
       (reset! update-new-messages-atom
               (timer/callOnce
                #(do (request-new-messages)
                     (request-read-messages)
                     (reset! update-new-messages-atom nil))
                10000)))))

  (def
    ^{:private true
      :doc "whenever the user send a message, the list of
            of unanswered messages is updated with very short
            delay to ensure that this message appears there.
            Furthermore the read message list has to be
            updated, too in order to ensure that the reply
            button disappears."}
    send-new-message-reactor
    (dispatch/react-to
     #{:msg-compose-dialog-confirmed}
     (fn [evt data]
       (when @update-unanswered-messages-atom ; clear any previously defined timer
         (timer/clear @update-unanswered-messages-atom))
       (reset! update-unanswered-messages-atom
               (timer/callOnce
                #(do
                   (loginfo "*** request new list for unanswered contacts! ***")
                   (request-unanswered-messages)
                   (request-read-messages) ; in order to update the send button
                   (reset! update-unanswered-messages-atom nil))
                2000)))))


  (defn- request-new-messages-when-available
    "checks whether new messages are available by first
     polling the sha1 hash over the new messages string.
     Only in case the sha1 does not match, reload them."
    []
    (send-request "/unread-messages-sha1"
                  {}
                  (fn [ajax-evt]
                    (let [resp-sha1 (. (. ajax-evt -target) (getResponseText))]
                      (when-not (= resp-sha1 @new-messages-sha1-atom)
                        ; (loginfo (str "server-sha1: " @new-messages-sha1-atom))
                        ; (loginfo (str "client-sha1: " resp-sha1))
                        (loginfo "sha of new messages hash changed, request new list from server")
                        (request-new-messages)
                        (request-unanswered-messages)))
                      )))


  (defn- poll-new-msgs
    "polling function checking for new messages"
    []
    (when-not @update-new-messages-atom
      ; (loginfo "*** poll sha of new messages ***")
      (request-new-messages-when-available)
      ))


  ;; start polling for new messages
  (def poll-for-new-msgs-timer (goog.Timer. 3000))
  (events/listen poll-for-new-msgs-timer goog.Timer.TICK poll-new-msgs)
  (. poll-for-new-msgs-timer (start))


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

      ;; request new messages, will be improved (polling, etc.)
      (request-new-messages)
      (request-read-messages)
      (request-unanswered-messages)

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

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
        [project-alpha-client.lib.ajax :only [send-request]]))

;;; the profile page (client side equivalent to index.html)
(def status-pane (dom/get-element "status-pane"))

(when status-pane


  ;; instantiate the compose message dialog
  (let [[dialog ok-button cancel-button]
        (get-modal-dialog
         :panel-id "compose-msg-dialog"
         :title-id "compose-msg-dialog-title"
         :ok-button-id "confirm-compose-msg"
         :cancel-button-id "cancel-compose-msg"
         :dispatched-event :msg-compose-dialog-confirmed)]
    (def msg-compose-dialog dialog)
    (def confirm-msg-compose-button ok-button)
    (def cancel-msg-compose-button cancel-button))

  (def editor (editor/create "cmp-msg-editor" "cmp-msg-toolbar"))
  ;; (open-modal-dialog msg-compose-dialog)
  (. editor (setHtml false txt true))
  (. editor (makeEditable))


  (defn- render-ref-mail
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


  (defn open-compose-msg-dialog
    "opens a new compose message dialog with the addresse and a table
     of all previous messages as arguments."
    [user-name referenced-msg-tab]
    (let [msg-title (get-element "compose-msg-dialog-title" status-pane)
          msg-title (str (goog.dom.getTextContent msg-title) user-name)]
      (. msg-compose-dialog (setTitle msg-title)))
    (render-ref-mail msg-compose-dialog (reduce str referenced-msg-tab))
    (set-compose-enabled-state true)
    (open-modal-dialog msg-compose-dialog)
    )


  (defn open-show-msg-dialog
    "opens the same dialog as with open-compose-msg-dialog but
     shows only the message stream and hides the editor pane"
    [user-name msg-tab]
    (let [msg-title (get-element "show-msg-dialog-title" status-pane)
          msg-title (str (goog.dom.getTextContent msg-title) user-name)]
      (. msg-compose-dialog (setTitle msg-title)))
    (render-ref-mail msg-compose-dialog (reduce str msg-tab))
    (set-compose-enabled-state false)
    (open-modal-dialog msg-compose-dialog)
    )


  (comment usage illustration
     (open-compose-msg-dialog "Sabinchen" ["<p>Message from Sabinchen</p>" "<p>Previous message from me</p>"])
     (open-show-msg-dialog "Sabinchen" ["<p>Message from Sabinchen</p>" "<p>Previous message from me</p>"])
     )



  ; --- receive and sent messages tab pane ---
  (def tabpane (goog.ui.TabPane. (get-element "msg-tab-pane" status-pane)))
  (. tabpane (addPage (TabPane/TabPage. (get-element "page1" status-pane))))
  (. tabpane (addPage (TabPane/TabPage. (get-element "page2" status-pane))))

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

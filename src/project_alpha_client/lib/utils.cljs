;;;
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; ====== utility functions ======
;;;
;;; 2011-11-23, Otto Linnemann


(ns project-alpha-client.lib.utils
  (:require [project-alpha-client.lib.json :as json]
            [project-alpha-client.lib.dispatch :as dispatch]
            [clojure.browser.event :as event]
            [clojure.browser.dom   :as dom]
            [goog.ui.Dialog :as Dialog]
            [goog.ui.Button :as Button]
            [goog.ui.FlatButtonRenderer :as FlatButtonRenderer]
            [goog.net.XhrIo :as ajax]
            [goog.events :as events]
            [goog.events.EventType :as event-type]
            [goog.style :as style])
  (:use [project-alpha-client.lib.logging :only [loginfo]]))


(defn send-request
  "send XHTTP request as string"
  ([url str] (send-request url str (fn [e] nil) "GET"))
  ([url str function] (send-request url str function "GET"))
  ([url str function method]
     (goog.net.XhrIo/send url function method str (json/clj->js {"Content-Type" ["application/json"]}))))


(defn validate-email
  "validates email address for correct canonical form.
   returns true if email string is valid, otherwise false."
  [email-str]
  (not (or
        (not (re-seq #"@" email-str))
        (re-seq #"@\." email-str)
        (re-seq #"\.@" email-str)
        (re-seq #"@.*@" email-str)
        (re-seq #"^\." email-str)
        (re-seq #"\.$" email-str)
        (re-seq #"\.{2,}" email-str))))


(defn copy-id-text
  "copies the text which is refered by given HTML id
       string to the 'to-id-str' HTML id."
  [from-id-str to-id-str]
  (let [from-elem (dom/get-element from-id-str)
        to-elem (dom/get-element to-id-str)]
    (goog.dom.setTextContent to-elem (goog.dom.getTextContent from-elem))))


(defn clear-id-text
  "clears the given HTML id element text"
  [id-str]
  (let [elem (dom/get-element id-str)]
    (goog.dom.setTextContent elem " ")))

(comment
  usage illustration

  (copy-id-text "name_not_available_error" "register_message_name")
  (clear-id-text "register_message_name" ""))


(defn- setup-modal-dialog-panel
  "retrieves invisible dom element for dialog pane
   for given id string moves the element to a
   foo.ui.Dialog element which is returned."
  [dom-id-str]
  (if-let [pane-element (dom/get-element dom-id-str)]
    (let [dialog (goog.ui.Dialog.)]
      (. dialog (setContent (goog.dom.getOuterHtml pane-element)))
      (goog.dom.removeNode pane-element)
      (. dialog (render))
      (style/setStyle (. dialog (getElement)) "z-index", "3")
      (style/setOpacity (dom/get-element dom-id-str) 1)
      dialog)))


(defn get-modal-dialog
  "constracts and setup a modal dialog panel from
   the specified dom element identifiers (given
   as strings) and returns vector with corresponding
   document objects for pane, ok-button and cancel
   button if given. Fires the event :dialog-closed
   when cancel or the close box is clicked."
  [& {:keys [panel-id
             title-id
             ok-button-id
             cancel-button-id
             dispatched-event
             dispatched-data]}]
  (let [dialog (setup-modal-dialog-panel panel-id)
        ok-button (goog.ui.decorate (dom/get-element ok-button-id))]
    (when title-id
      (. dialog (setTitle
                 (goog.dom.getTextContent (dom/get-element title-id)))))
    (. dialog (setButtonSet null))
    (set! (.panel-id dialog) panel-id)
    (. ok-button (setEnabled true))
    (events/listen ok-button "action"
                   #(do (. dialog (setVisible false))
                        (dispatch/fire dispatched-event dispatched-data)))
    (events/listen dialog "afterhide"
                   #(dispatch/fire :dialog-closed panel-id))
    (if cancel-button-id
      (let [cancel-button (goog.ui.decorate (dom/get-element cancel-button-id))]
        (events/listen cancel-button "action" #(. dialog (setVisible false)))
        (. cancel-button (setEnabled true))
        [dialog ok-button cancel-button])
      [dialog ok-button])))


(defn open-modal-dialog
  "Opens the given dialog and fires the event :dialog-opened"
  [dialog]
  (. dialog (setVisible true))
  (dispatch/fire :dialog-opened (.panel-id dialog)))


(defn init-alpha-button
  "function to create a goog.ui.Button instance from
   an html input button element (alpha-button) with
   given dom id which also attaches a clojurescript
   event (dispatch mechanism)."
  [button-id event]
  (let [button (goog.ui.Button.)]
    (. button (decorate (dom/get-element button-id)))
    (events/listen button "action" #(dispatch/fire event nil))
    button))


(defn set-alpha-button-enabled
  "function to enable or disabled the alpha-button
   state."
  [goog-button state]
  (let [button (. goog-button (getElement))
        opacity (if state 1.0 0.5)]
    (. goog-button (setEnabled state))
    (style/setOpacity button opacity)))


(defn is-alpha-button-enabled
  "true when button enabled"
  [goog-button]
  (let [button (. goog-button (getElement))]
    (. goog-button (isEnabled))))

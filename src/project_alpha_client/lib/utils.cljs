;;;
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the GNU General Public License
;;;
;;; ====== utility functions ======
;;;
;;; 2011-11-23, Otto Linnemann


(ns project-alpha-client.lib.utils
  (:require [project-alpha-client.lib.json :as json]
            [project-alpha-client.lib.dispatch :as dispatch]
            [clojure.browser.event :as event]
            [clojure.browser.dom   :as dom]
            [goog.dom :as gdom]
            [goog.ui.Dialog :as Dialog]
            [goog.ui.Button :as Button]
            [goog.ui.FlatButtonRenderer :as FlatButtonRenderer]
            [goog.net.XhrIo :as ajax]
            [goog.events :as events]
            [goog.events.EventType :as event-type]
            [goog.style :as style]
            [goog.string :as gstring]
            [goog.string.format :as gformat])
  (:use [project-alpha-client.lib.logging :only [loginfo]]))


(defn- current-url-keyword
  "returns the keyword matching to the currently opened URL (page)
   This allows to select the page the client renders by the URL.
   In example for http://project-alpha/index.html we get the
   symbol :index which per definition is used used for the client
   side rendering via switch-to-page function."
  []
  (let [[page-html page]
        (first (re-seq #"([a-zA-Z_]*)\.html$" (js* "document.URL")))]
    (keyword page)))


(defn get-element
  "similar to dom/get-element but the search can be
   restricted to a given node (2nd argument) If no
   node is specified the document object is searched."
  ([element] (get-element element (gdom/getDocument)))
  ([element node]
      (gdom/findNode node
                     (fn [e] (= (. e -id) element)))))


(defn get-button-group-value
  "reads a button group with given name specifiers and returns
   a hash map for all selected entries."
  [button-group-name]
  (let [rb (gdom/findNodes (gdom/getDocument)
                           (fn [e] (= (. e -name) button-group-name)))
        harray (map #(if (. % -checked) (hash-map (. % -name) (. % -value))) rb)]
    (apply merge harray)))


(defn set-button-group-value
  "sets button group to specified value."
  [button-group-name value-set]
  (let [rb (gdom/findNodes (gdom/getDocument)
                           (fn [e] (= (. e -name) button-group-name)))]
    (doseq [b rb] (set! (. b -checked) (contains? value-set (. b -value))))
    ))


(comment
  "usage illustration"
  (get-button-group-value "user_sex")
  (set-button-group-value "user_sex" #{"male"}))


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
             dispatched-data
             keep-open]}]
  (let [dialog (setup-modal-dialog-panel panel-id)]
    (when title-id
      (. dialog (setTitle
                 (goog.dom.getTextContent (dom/get-element title-id)))))
    (. dialog (setButtonSet js/null))
    (set! (. dialog -panel-id) panel-id)
    (events/listen dialog "afterhide"
                   #(dispatch/fire :dialog-closed panel-id))
    (let [cancel-button
          (when cancel-button-id
            (let [button (goog.ui.decorate (dom/get-element cancel-button-id))]
              (events/listen button "action" #(. dialog (setVisible false)))
              (. button (setEnabled true))
              button))
          ok-button
          (when ok-button-id
            (let [button (goog.ui.decorate (dom/get-element ok-button-id))]
              (events/listen button "action"
                             #(do
                                (when-not keep-open (. dialog (setVisible false)))
                                (dispatch/fire dispatched-event dispatched-data)))
              (. button (setEnabled true))
              button))]
      [dialog ok-button cancel-button])))


(defn open-modal-dialog
  "Opens the given dialog and fires the event :dialog-opened"
  [dialog]
  (. dialog (setVisible true))
  (dispatch/fire :dialog-opened (. dialog -panel-id)))


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


(defn is-ios-device?
  "true on ipad, phone, ipod"
  []
  (js/eval "navigator.userAgent.match(/(iPad|iPhone|iPod)/i) ? true : false"))


(defn show-elements-of-class
  "show (normally hidden) elements of given class"
  [class]
  (let [ios-nodes (gdom/findNodes (gdom/getDocument)
                                  (fn [e] (= (. e -className) class)))]
    (dorun (map #(set! (. (. % -style) -display) "inline") ios-nodes))))


(defn str-replace
  "clojure.string/replace does not work anymore with old versions
     of ClojureScript, so provide a replacement here."
  [s regex-string repl]
  (let [regex (js/RegExp. regex-string "g")]
    (. s (replace regex repl))))

(comment
  (str-replace "abc <img myimage.jpg> def" "<img[^>]*>" "")
  )

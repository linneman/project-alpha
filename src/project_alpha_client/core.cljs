;;;
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; 2011-11-23, Otto Linnemann

(ns project-alpha-client.core
  (:require [project-alpha-client.json :as json]
            [project-alpha-client.editor :as editor]
            [clojure.browser.event :as event]
            [clojure.browser.dom   :as dom]
            [goog.net.XhrIo :as ajax]
            [goog.net.XhrManager :as XhrManager]
            [goog.style :as style]
            [goog.events :as events]
            [goog.object :as object]
            [goog.ui.Component :as Component]
            [goog.ui.Button :as Button]
            [goog.ui.TabPane :as TabPane]
            [goog.ui.FlatButtonRenderer :as FlatButtonRenderer]
            [goog.ui.Dialog :as Dialog]
            [goog.Timer :as timer]
            [goog.debug.Console :as Console]
            [goog.debug.Logger :as Logger]
            [goog.debug.Logger.Level :as LogLevel]))

(def debugConsole (goog.debug.Console. "core"))
(. debugConsole (setCapturing true))

(def logger (Logger/getLogger "project-alpha.core"))
(. logger (setLevel LogLevel/ALL))

(defn loginfo [msg]
  (. logger (log LogLevel/INFO msg)))

(def current-url (js* "document.URL"))

(loginfo (str "CURRENT-URL: " current-url))

; -------------
(comment
  (def parent-element (dom/get-element "bg"))
  (def mydom (goog.dom.getDomHelper parent-element))
  (def mycomponent (goog.ui.Component. mydom))
  (set! (.IdFragment mycomponent) {"ICON" "ic" "LABEL" "la"})


  ; Javascript based way
  (set! (.createDom mycomponent)
        (js* "function() { var element=goog.dom.createDom('div', {'id': '123', 'class': 'Otto'}, 'Hallo Welt!'); this.setElementInternal(element);};"))

  ; if mycompoment was a class we would extend its prototype this way in Clojurescript
  (set! (.prototype mycomponent)
        (json/clj->js
         { "createDom"
           (fn [] (. mycomponent (setElementInternal (goog.dom.createDom "class" null)))) }))

  ; since we did not create another class here we directly overwrite createDom
  (set! (.createDom mycomponent)
        (fn [] (. mycomponent (setElementInternal (goog.dom.createDom "div" (json/clj->js {"id" "123" "class" "Otto"}) "Hallo Welt!")))))

  ; render it!
  (. mycomponent (render parent-element)))
; -------------

; =============
(comment
  ;(def mybutton (goog.ui.Button. "Hello!"))
  (def mybutton (goog.ui.Button. "Hello!" (FlatButtonRenderer/getInstance)))
  (. mybutton (render parent-element))
  (. mybutton (setTooltip "my tooltip"))

  (goog.object.getValues goog.ui.Component.EventType)
  (events/listen mybutton "action" #(js/alert "button pressed")))
; =============

(def myobj (json/clj->js {"key1" "value1" "key2" #(println "Hallo Welt")}))
;(println ((.key2 myobj)))

;((goog.bind #(println (js* "this.key1")) myobj))

(def parent-element (dom/get-element "bg"))
(def mydom (goog.dom.getDomHelper parent-element))
(def mycomponent (goog.ui.Component. mydom))
(set! (.IdFragment mycomponent) {"ICON" "ic" "LABEL" "la"})
; (set! (.createDom mycomponent)
;       (js* "function() { var element=goog.dom.createDom('div', {'id': '123', 'class': 'Otto'}, 'Hallo Welt!'); this.setElementInternal(element);};"))

;(. mycomponent (render parent-element))




    ;; ====== utility functions ======


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



    ;; ====== page functions ======

(defn ^:export profile
  "functions for profile pane"
  []
  (do
    ;;
    ;;
    (def tabpane (goog.ui.TabPane. (dom/get-element "tabpane1")))
    (. tabpane (addPage (TabPane/TabPage. (dom/get-element "page1"))))
    (. tabpane (addPage (TabPane/TabPage. (dom/get-element "page2"))))
    (. tabpane (addPage (TabPane/TabPage. (dom/get-element "page3"))))

    (def editor (editor/create "editMe" "toolbar"))

    (events/listen editor goog.editor.Field.EventType.DELAYEDCHANGE
                   (fn [e]
                     (loginfo (json/generate {"text" (. editor (getCleanContents))}))
                     (send-request "/profile"
                                   (json/generate {"text" (. editor (getCleanContents))})
                                   (fn [e] nil)
                                   "POST")))
    ))



(defn ^:export register
  "functions for register pane"
  []
  (do

    ;; move the registration form to modal dialog panel
    (def register-pane-elem (dom/get-element "register"))
    (def dialog (goog.ui.Dialog.))
    (. dialog (setContent (goog.dom.getOuterHtml register-pane-elem)))
    (goog.dom.removeNode register-pane-elem)
    (. dialog (render))
    (def register-pane-elem (dom/get-element "register"))

    (. dialog (setTitle
               (goog.dom.getTextContent (dom/get-element "register-dialog-title"))))
    (. dialog (setButtonSet null))
    (style/setOpacity register-pane-elem 1)
    (. dialog (setVisible true))

    (def confirm-button (goog.ui.decorate (dom/get-element "confirm-registration")))

    (def cancel-button (goog.ui.decorate (dom/get-element "cancel-registration")))
    (. cancel-button (setEnabled true))


    (defn- when-user-exists
      "function is executed when user does exists
       with user data as argument."
      [name function]
      (send-request (str "/user/" (goog.string.urlEncode name)) nil
                    (fn [e] (let [text (. (.target e) (getResponseText))
                                  data (json/parse text)]
                              (if (not-empty data) (function data))))))

    (comment
      usage illustration

      (when-user-exists "Otto" #(loginfo (pr-str "User exists, data: " %)))
      (when-user-exists "linneman@gmx.de" #(loginfo (pr-str "User exists, data: " %)))
      (when-user-exists "Otto" #(loginfo (% "name")))
      (when-user-exists "Otto2" (fn [data] (loginfo (data "name"))))
      )


    (def reg-form-status (atom
                          {:name "undefined"
                           :email "undefined"
                           :password "undefined"
                           :password-repeat "undefined"}))

    (defn- set-name-error
      "set an error message with respect to name field"
      [dom-error-id-string]
      (swap! reg-form-status assoc :name dom-error-id-string)
      (copy-id-text dom-error-id-string "register_message_name")
      (set! (.color (.style (dom/get-element "name"))) "red")
      )

    (defn- clear-name-error
      "remove all error message with respect to name field"
      []
      (swap! reg-form-status dissoc :name)
      (clear-id-text "register_message_name")
      (set! (.color (.style (dom/get-element "name"))) "green")
      )

    (defn- set-email-error
      "set an error message with respect to email field"
      [dom-error-id-string]
      (swap! reg-form-status assoc :email dom-error-id-string)
      (copy-id-text dom-error-id-string "register_message_email")
      (set! (.color (.style (dom/get-element "email"))) "red")
      )

    (defn- clear-email-error
      "remove all error message with respect to email field"
      []
      (swap! reg-form-status dissoc :email)
      (clear-id-text "register_message_email")
      (set! (.color (.style (dom/get-element "email"))) "green")
      )

    (defn- set-password-error
      "set an error message with respect to password field"
      [dom-error-id-string]
      (swap! reg-form-status assoc :password dom-error-id-string)
      (copy-id-text dom-error-id-string "register_message_password")
      (set! (.color (.style (dom/get-element "password"))) "red")
      )

    (defn- clear-password-error
      "remove all error message with respect to password field"
      []
      (swap! reg-form-status dissoc :password)
      (clear-id-text "register_message_password")
      (set! (.color (.style (dom/get-element "password"))) "green")
      )

    (defn- set-password-repeat-error
      "set an error message with respect to password repeat field"
      [dom-error-id-string]
      (swap! reg-form-status assoc :password-repeat dom-error-id-string)
      (copy-id-text dom-error-id-string "register_message_password_repeat")
      (set! (.color (.style (dom/get-element "password-repeat"))) "red")
      )

    (defn- clear-password-repeat-error
      "remove all error message with respect to password repeat field"
      []
      (swap! reg-form-status dissoc :password-repeat)
      (clear-id-text "register_message_password_repeat")
      (set! (.color (.style (dom/get-element "password-repeat"))) "green")
      )

    (defn- update-confirm-button-state []
      (if (empty? @reg-form-status)
        (do (. confirm-button (setEnabled true)) true)
        (do (. confirm-button (setEnabled false)) false)))


    (defn- updateRegisterText
      "validates registration form"
      [e]
      (let [target (.target e)
            target-id (.id target)
            target-elem (dom/get-element target-id)
            value (.value target-elem)]
        (loginfo (str "focus out event triggered for: " target-id))
        (cond
         (= target-id "name")
         (do
           (loginfo (str "name->" value))
           (clear-name-error)
           (when-user-exists value
                             (fn [data]
                               (do
                                 (loginfo (pr-str "User " value " exists already!"))
                                 (set-name-error "name_not_available_error")))))
         (= target-id "email")
         (do
           (loginfo (str "email->" value))
           (clear-email-error)
           (when-user-exists value
                             (fn [data]
                               (do
                                 (loginfo (pr-str "Emailaddress " value " exists already!"))
                                 (set-email-error "email_defined_error"))))
           (when (not (validate-email value))
             (loginfo (pr-str "Emailaddress " value " is malformed!"))
             (set-email-error "email_malformed_error")))
         (= target-id "password")
         (do
           (loginfo (str "password->" value))
           (clear-password-error)
           (if (< (count value) 5)
             (do
               (loginfo (pr-str "Password " value " too short"))
               (set-password-error "password_form_error"))))
         (= target-id "password-repeat")
         (do
           (loginfo (str "password-repeat->" value))
           (clear-password-repeat-error)
           (if (not= value (.value (dom/get-element "password")))
             (do
               (loginfo (pr-str "Password " value " do not match!"))
               (set-password-repeat-error "password_mismatch_error"))
             ))
         )
        (update-confirm-button-state)))


    (defn- check-all-reg-fields
      "checks all register pane fields.
       This is required before final transmission"
      []
      (dorun (map
              #(updateRegisterText
                (goog.events.Event. "focusout" (dom/get-element %)))
              ["name" "email" "password" "password-repeat"]))
      (update-confirm-button-state))


    (def reg-field-poll-timer (goog.Timer. 500))

    (defn- start-polling-all-reg-field-checks
      "start fields checking every 500ms to send button to be updated
       even when last input field has not been left."
      []
      (do  (. reg-field-poll-timer (start))
           (events/listen reg-field-poll-timer
                          goog.Timer/TICK check-all-reg-fields)))

    (defn- stop-polling-all-reg-field-checks
      "stops field checking"
      []
      (. reg-field-poll-timer (stop)))


    (defn- trigger-polling-when-entered-last-field
      [e]
      (let [target (.target e)
            target-id (.id target)
            target-elem (dom/get-element target-id)
            value (.value target-elem)]
        (if
         (= target-id "password-repeat")
         (start-polling-all-reg-field-checks)
         (stop-polling-all-reg-field-checks))))

    (def registerFieldsFocusHandler (goog.events.FocusHandler. register-pane-elem))

    (events/listen
     registerFieldsFocusHandler
     goog.events.FocusHandler.EventType.FOCUSOUT
     updateRegisterText)

    (events/listen
     registerFieldsFocusHandler
     goog.events.FocusHandler.EventType.FOCUSIN
     trigger-polling-when-entered-last-field)

    (events/listen cancel-button "action" #(. dialog (setVisible false)))
    (events/listen confirm-button
                   "action"
                   #(do (if (check-all-reg-fields)
                          (send-request "/register"
                                        (json/generate {"name" (.value (dom/get-element "name"))
                                                        "email" (.value (dom/get-element "email"))
                                                        "password" (.value (dom/get-element "password"))})
                                        (fn [e] nil)
                                        "POST"))
                        (. dialog (setVisible false))))

    (comment
      (def a (dom/get-element "name"))
      (.value a)
      (set! (.value a) "")
      (set! (.color (.style a)) "red"))

    ))


; =============

; main entry point
(defn ^:export init []
  )

;; invoke main function
;; (init)




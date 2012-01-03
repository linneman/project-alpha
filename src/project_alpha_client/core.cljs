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

    (defn updateRegisterText
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
           (set! (.color (.style target-elem)) "green")
           (clear-id-text "register_message_name")
           (when-user-exists value
                             (fn [data]
                               (do
                                 (loginfo (pr-str "User " value " exists already!"))
                                 (set! (.color (.style target-elem)) "red")
                                 (copy-id-text
                                  "name_not_available_error"
                                  "register_message_name")))))
         (= target-id "email")
         (do
           (loginfo (str "email->" value))
           (set! (.color (.style target-elem)) "green")
           (clear-id-text "register_message_email")
           (when-user-exists value
                             (fn [data]
                               (do
                                 (loginfo (pr-str "Emailaddress " value " exists already!"))
                                 (set! (.color (.style target-elem)) "red")
                                 (copy-id-text
                                  "email_defined_error"
                                  "register_message_email"))))
           (when (not (validate-email value))
             (loginfo (pr-str "Emailaddress " value " is malformed!"))
             (set! (.color (.style target-elem)) "red")
             (copy-id-text "email_malformed_error" "register_message_email")))
         (= target-id "password")
         (do
           (loginfo (str "password->" value))
           (set! (.color (.style target-elem)) "green")
           (clear-id-text "register_message_password")
           (if (< (count value) 5)
             (do
               (loginfo (pr-str "Password " value " too short"))
               (set! (.color (.style target-elem)) "red")
               (copy-id-text "password_form_error" "register_message_password")
               )))
         (= target-id "password-repeat")
         (do
           (loginfo (str "password-repeat->" value))
           (set! (.color (.style target-elem)) "green")
           (clear-id-text "register_message_password_repeat")
           (if (not= value (.value (dom/get-element "password")))
             (do
               (loginfo (pr-str "Password " value " do not match!"))
               (set! (.color (.style target-elem)) "red")
               (copy-id-text "password_mismatch_error" "register_message_password_repeat")
               )
             ))
         )))


    (def registerFields (dom/get-element "register"))
    (def registerFieldsFocusHandler (goog.events.FocusHandler. registerFields))

    (events/listen
     registerFieldsFocusHandler
     goog.events.FocusHandler.EventType.FOCUSOUT
     updateRegisterText)

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




;;; project-alph
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



(if (re-seq #"index\.html$" current-url)
  (do
    ;;
    ;; ====== functions for profile pane ======
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
    )) ; (if (re-seq #"index\.html$" current-url))




(if (re-seq #"register\.html$" current-url)
  (do
    ;;
    ;; ====== functions for register pane ======
    ;;

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
      (when-user-exists "Otto" #(loginfo (% "name")))
      (when-user-exists "Otto2" (fn [data] (loginfo (data "name"))))
      )

    (defn updateRegisterText [e]
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
           (when-user-exists value
                             (fn [data]
                               (do
                                 (loginfo (pr-str "User " value " exists already!"))
                                 (set! (.color (.style target-elem)) "red")))))
         (= target-id "email") (loginfo (str "email->" value))
         (= target-id "password") (loginfo (str "password->" value))
         (= target-id "password-repeat") (loginfo (str "password-repeat->" value)))))


    (def registerFields (dom/get-element "register"))
    (def registerFiedlsFocusHandler (goog.events.FocusHandler. registerFields))


    (events/listen
     registerFiedlsFocusHandler
     goog.events.FocusHandler.EventType.FOCUSOUT
     updateRegisterText)

    (comment
      (def a (dom/get-element "name"))
      (.value a)
      (set! (.value a) "")
      (set! (.color (.style a)) "red"))

    )) ; (if (re-seq #"register\.html$" current-url))




; =============

; main entry point
(defn ^:export init []
  )

;; invoke main function
;; (init)




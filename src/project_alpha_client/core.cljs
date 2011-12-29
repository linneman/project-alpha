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

; =============

(def tabpane (goog.ui.TabPane. (dom/get-element "tabpane1")))
(. tabpane (addPage (TabPane/TabPage. (dom/get-element "page1"))))
(. tabpane (addPage (TabPane/TabPage. (dom/get-element "page2"))))
(. tabpane (addPage (TabPane/TabPage. (dom/get-element "page3"))))

; =============

(defn- send-event
  "send XHTTP request as string"
  [url str]
  (goog.net.XhrIo/send url (fn [e] nil) "POST" str (json/clj->js {"Content-Type" ["application/json"]})))


(def editor (editor/create "editMe" "toolbar"))

(events/listen editor goog.editor.Field.EventType.DELAYEDCHANGE
               (fn [e]
                 (loginfo (json/generate {"text" (. editor (getCleanContents))}))
                 (send-event "/profile"  (json/generate {"text" (. editor (getCleanContents))}))))

; =============

; main entry point
(defn ^:export init []
  )

;; invoke main function
;; (init)




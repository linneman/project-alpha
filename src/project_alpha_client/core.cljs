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
            [clojure.browser.event :as event]
            [clojure.browser.dom   :as dom]
            [goog.net.XhrIo :as ajax2]
            [goog.style :as style]
            [goog.events :as events]
            [goog.object :as object]
            [goog.ui.Component :as Component]
            [goog.ui.Button :as Button]
            [goog.ui.TabPane :as TabPane]
            [goog.ui.FlatButtonRenderer :as FlatButtonRenderer]
            [goog.editor.Field :as EditorField]
            [goog.editor.Command :as EditorCommand]
            [goog.editor.plugins.BasicTextFormatter :as BasicTextFormatter]
            [goog.editor.plugins.RemoveFormatting :as RemoveFormatting]
            [goog.editor.plugins.UndoRedo :as UndoRedo]
            [goog.editor.plugins.ListTabHandler :as ListTabHandler]
            [goog.editor.plugins.SpacesTabHandler :as SpacesTabHandler]
            [goog.editor.plugins.EnterHandler :as EnterHandler]
            [goog.editor.plugins.HeaderFormatter :as HeaderFormatter]
            [goog.editor.plugins.LinkDialogPlugin :as LinkDialogPlugin]
            [goog.editor.plugins.LinkBubble :as LinkBubble]
            [goog.ui.editor.DefaultToolbar :as DefaultToolbar]
            [goog.ui.editor.ToolbarController :as ToolbarController]
            [goog.Timer :as timer]))


(defn log
  "logging (requires firebug plugin in firefox)"
  [& args]
  (let [logstr (apply pr-str args)]
    (js* "(function() { if (this.console && typeof console.log != 'undefined') console.log(~{logstr}); })();")))



; -------------
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
(. mycomponent (render parent-element))
; -------------

; =============
;(def mybutton (goog.ui.Button. "Hello!"))
(def mybutton (goog.ui.Button. "Hello!" (FlatButtonRenderer/getInstance)))
(. mybutton (render parent-element))
(. mybutton (setTooltip "my tooltip"))

(goog.object.getValues goog.ui.Component.EventType)
(events/listen mybutton "action" #(js/alert "button pressed"))
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
(def myField (goog.editor.Field. "editMe"))
(. myField (registerPlugin (goog.editor.plugins.BasicTextFormatter.)))
(. myField (registerPlugin (goog.editor.plugins.RemoveFormatting.)))
(. myField (registerPlugin (goog.editor.plugins.UndoRedo.)))
(. myField (registerPlugin (goog.editor.plugins.ListTabHandler.)))
(. myField (registerPlugin (goog.editor.plugins.SpacesTabHandler.)))
(. myField (registerPlugin (goog.editor.plugins.EnterHandler.)))
(. myField (registerPlugin (goog.editor.plugins.HeaderFormatter.)))
(. myField (registerPlugin (goog.editor.plugins.LinkDialogPlugin.)))
(. myField (registerPlugin (goog.editor.plugins.LinkBubble.)))

(def buttons (json/clj->js
              [
               EditorCommand/BOLD
               EditorCommand/ITALIC
               EditorCommand/UNDERLINE
               EditorCommand/FONT_COLOR
               EditorCommand/BACKGROUND_COLOR
               EditorCommand/FONT_FACE
               EditorCommand/FONT_SIZE
               EditorCommand/LINK
               EditorCommand/UNDO
               EditorCommand/REDO
               EditorCommand/UNORDERED_LIST
               EditorCommand/ORDERED_LIST
               EditorCommand/INDENT
               EditorCommand/OUTDENT
               EditorCommand/JUSTIFY_LEFT
               EditorCommand/JUSTIFY_CENTER
               EditorCommand/SUBSCRIPT
               EditorCommand/SUPERSCRIPT
               EditorCommand/STRIKE_THROUGH
               EditorCommand/REMOVE_FORMAT
               ]))

(def myToolbar (DefaultToolbar/makeToolbar buttons (dom/get-element "toolbar")))
(def myToolbarController (goog.ui.editor.ToolbarController. myField myToolbar))

(. myField (makeEditable))


; =============

; main entry point
(defn ^:export init []
  )

;; invoke main function
;; (init)




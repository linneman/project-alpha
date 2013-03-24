;;; wrapper to google editor
;;;
;;; The use and distribution terms for this software are covered by
;;; the GNU General Public License

(ns project-alpha-client.lib.editor
  (:require [project-alpha-client.lib.json :as json]
            [clojure.browser.dom   :as dom]
            [goog.net.XhrIo :as ajax2]
            [goog.events :as events]
            [goog.object :as object]
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
            [goog.ui.editor.ToolbarController :as ToolbarController]))

(defn create
  "create a new google editor pane with the dom id's
   'field-id' for the pane and 'toolbar-id' for the
   toolbar."
  [field-id toolbar-id]
  (let [myField (goog.editor.Field. field-id)
        buttons (json/clj->js
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
                   EditorCommand/JUSTIFY_RIGHT
                   ;; EditorCommand/SUBSCRIPT
                   ;; EditorCommand/SUPERSCRIPT
                   ;; EditorCommand/STRIKE_THROUGH
                   EditorCommand/REMOVE_FORMAT
                   ])
        myToolbar (DefaultToolbar/makeToolbar buttons (dom/get-element toolbar-id))
        myToolbarController (goog.ui.editor.ToolbarController. myField myToolbar)
        ]
    (. myField (registerPlugin (goog.editor.plugins.BasicTextFormatter.)))
    (. myField (registerPlugin (goog.editor.plugins.RemoveFormatting.)))
    (. myField (registerPlugin (goog.editor.plugins.UndoRedo.)))
    (. myField (registerPlugin (goog.editor.plugins.ListTabHandler.)))
    (. myField (registerPlugin (goog.editor.plugins.SpacesTabHandler.)))
    (. myField (registerPlugin (goog.editor.plugins.EnterHandler.)))
    (. myField (registerPlugin (goog.editor.plugins.HeaderFormatter.)))
    (. myField (registerPlugin (goog.editor.plugins.LinkDialogPlugin.)))
    (. myField (registerPlugin (goog.editor.plugins.LinkBubble.)))
    ; (. myField (makeEditable)) causes NS_ERROR_FAILER Exception of FS when not displayed!
    myField))

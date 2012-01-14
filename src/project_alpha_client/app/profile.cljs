;;;
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; functions for profile page
;;;
;;; 2011-11-23, Otto Linnemann

(ns project-alpha-client.app.profile
  (:require [project-alpha-client.lib.json :as json]
            [project-alpha-client.lib.editor :as editor]
            [clojure.browser.dom :as dom]
            [goog.style :as style]
            [goog.events :as events]
            [goog.ui.Button :as Button]
            [goog.ui.TabPane :as TabPane]
            )
  (:use [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.utils :only [send-request]]))

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

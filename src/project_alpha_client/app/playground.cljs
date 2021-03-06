;;;
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the GNU General Public License
;;;
;;; Test environment
;;;
;;; 2011-11-23, Otto Linnemann

(ns project-alpha-client.app.playground
  (:require [project-alpha-client.lib.json :as json]
            [project-alpha-client.lib.dispatch :as dispatch]
            [clojure.browser.event :as event]
            [clojure.browser.dom   :as dom]
            [goog.net.XhrIo :as ajax]
            [goog.net.XhrManager :as XhrManager]
            [goog.style :as style]
            [goog.events :as events]
            [goog.events.EventType :as event-type]
            [goog.object :as object]
            [goog.ui.Component :as Component]
            [goog.ui.Button :as Button]
            [goog.ui.TabPane :as TabPane]
            [goog.ui.FlatButtonRenderer :as FlatButtonRenderer]
            [goog.ui.Dialog :as Dialog]
            [goog.Timer :as timer])
  (:use [project-alpha-client.lib.logging :only [loginfo]])
  (:use-macros [macros.macros :only [hash-args]]))


(def current-url (js* "document.URL"))
(loginfo (str "CURRENT-URL: " current-url))


;; illustration of macro usage

(def h (let [a (fn [] "hello world!")
             b (fn [] "another function")
             c (fn [] 43)]
         (hash-args a b c)))

(loginfo (str "macro test: " ((:a h))))





;;;
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; functions for the imprint page
;;;
;;; 2011-11-23, Otto Linnemann

(ns project-alpha-client.app.repl
  (:require [project-alpha-client.lib.pages :as pages]
            [project-alpha-client.app.nav :as nav]
            [clojure.browser.dom :as dom]
            [goog.style :as style]
            [goog.events :as events]
            [goog.ui.Button :as Button]
            [goog.ui.TabPane :as TabPane]
            [project-alpha-client.lib.dispatch :as dispatch])
  (:use [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.ajax :only [send-request]]))

(def repl-pane (dom/get-element "repl-pane"))


(def site-enabled-reactor (dispatch/react-to
                           #{:page-switched}
                           (fn [evt data]
                             (if (= (:to data) :repl)
                               (enable-repl-page)
                               (disable-repl-page)))))

(defn- enable-repl-page
  "shows the repl-page"
  []
  (if repl-pane
    (do
      (style/setOpacity repl-pane 1) ;; important for first load only
      (style/showElement repl-pane true)
      (nav/enable-nav-pane)
      (loginfo "repl page enabled"))
    (do
      (pages/reload-url "/repl.html")
      (loginfo "repl page reloaded"))))


(defn- disable-repl-page
  "hides the repl-page"
  []
  (when repl-pane
    (style/showElement repl-pane false)
    (loginfo "repl page disabled")))

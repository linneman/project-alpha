;;;
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; functions for the status page
;;;
;;; 2011-11-23, Otto Linnemann

(ns project-alpha-client.app.status
  (:require [project-alpha-client.lib.pages :as pages]
            [project-alpha-client.app.nav :as nav]
            [project-alpha-client.lib.json :as json]
            [project-alpha-client.lib.editor :as editor]
            [clojure.browser.dom :as dom]
            [goog.style :as style]
            [goog.events :as events]
            [goog.ui.Button :as Button]
            [goog.ui.TabPane :as TabPane]
            [project-alpha-client.lib.dispatch :as dispatch])
  (:use [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.ajax :only [send-request]]))

;;; the profile page (client side equivalent to index.html)
(def status-pane (dom/get-element "status-pane"))


(def site-enabled-reactor (dispatch/react-to
                           #{:page-switched}
                           (fn [evt data]
                             (if (= (:to data) :status)
                               (enable-status-page)
                               (disable-status-page)))))

(defn- enable-status-page
  "shows the status-page"
  []
  (if status-pane
    (do
      (style/setOpacity status-pane 1) ;; important for first load only
      (style/showElement status-pane true)
      (nav/enable-nav-pane)
      (loginfo "status page enabled"))
    (do
      (pages/reload-url "/status.html")
      (loginfo "status page reloaded"))))


(defn- disable-status-page
  "hides the status-page, activates the status"
  []
  (when status-pane
    (style/showElement status-pane false)
    (loginfo "status page disabled")))

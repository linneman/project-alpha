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

(ns project-alpha-client.app.imprint
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
(def imprint-pane (dom/get-element "imprint-pane"))


(def site-enabled-reactor (dispatch/react-to
                           #{:page-switched}
                           (fn [evt data]
                             (if (= (:to data) :imprint)
                               (enable-imprint-page)
                               (disable-imprint-page)))))

(defn- enable-imprint-page
  "shows the imprint-page"
  []
  (if imprint-pane
    (do
      (style/setOpacity imprint-pane 1) ;; important for first load only
      (style/showElement imprint-pane true)
      (nav/enable-nav-pane)
      (loginfo "imprint page enabled"))
    (do
      (pages/reload-url "/imprint.html")
      (loginfo "imprint page reloaded"))))


(defn- disable-imprint-page
  "hides the imprint-page, activates the imprint"
  []
  (when imprint-pane
    (style/showElement imprint-pane false)
    (loginfo "imprint page disabled")))

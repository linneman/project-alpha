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

(ns project-alpha-client.app.reset-password
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
        [project-alpha-client.lib.utils :only [send-request]]))

;;; the reset password page (client side equivalent to index.html)
(def reset-password-pane (dom/get-element "reset-password-pane"))


(def site-enabled-reactor (dispatch/react-to
                           #{:page-switched}
                           (fn [evt data]
                             (loginfo (str "status-reactor, from: " (:from data) " to: "(:to data)))
                             (if (= (:to data) :reset_pw)
                               (enable-reset-password-page)
                               (disable-reset-password-page)))))

(defn- enable-reset-password-page
  "shows the page"
  []
  (if reset-password-pane
    (do
      (style/setOpacity reset-password-pane 1) ;; important for first load only
      (style/showElement reset-password-pane true)
      (nav/enable-nav-pane)
      (loginfo "reset password page enabled"))
    (do
      (pages/reload-url "/reset_pw.html")
      (loginfo "reset password page reloaded"))))


(defn- disable-reset-password-page
  "hides the page"
  []
  (when reset-password-pane
    (style/showElement reset-password-pane false)
    (loginfo "reset password page disabled")))

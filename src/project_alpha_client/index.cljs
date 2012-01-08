;;;
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; functions for very first page index.html
;;; requires the html/javascript blocks login and register
;;;
;;; 2011-11-23, Otto Linnemann

(ns project-alpha-client.index
  (:require [clojure.browser.dom   :as dom]
            [goog.events :as events])
  (:use [project-alpha-client.login :only [open-login-dialog]]
        [project-alpha-client.register :only [open-register-dialog]]
        [project-alpha-client.logging :only [loginfo]]
        [project-alpha-client.utils :only [send-request validate-email copy-id-text clear-id-text]]))


(def login-button (goog.ui.decorate (dom/get-element "login-button")))
(. login-button (setEnabled true))
(events/listen login-button "action" open-login-dialog)

(def register-button (goog.ui.decorate (dom/get-element "register-button")))
(. register-button (setEnabled true))
(events/listen register-button "action" open-register-dialog)

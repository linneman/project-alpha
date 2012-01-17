;;;
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; ====== functions for switching pages ======
;;;
;;; 2011-11-23, Otto Linnemann


(ns project-alpha-client.lib.pages
  (:require [project-alpha-client.lib.json :as json]
            [project-alpha-client.lib.dispatch :as dispatch]
            [clojure.browser.event :as event]
            [clojure.browser.dom   :as dom]
            [goog.events :as events]
            [goog.events.EventType :as event-type]
            [goog.style :as style])
  (:use [project-alpha-client.lib.logging :only [loginfo]]))

(def ^{:doc "atom which holds the keyword of the currently acitve page"
       :private true }
  page (atom nil))


(defn switch-to-page
  "switches to new page"
  [new-page]
  (swap! page #(when (not= % new-page)
                 (loginfo (str "switched to page " new-page))
                 (dispatch/fire :page-switched {:from page :to new-page})
                 new-page)))


(defn get-active-page
  "returns the currently active page"
  []
  @page)


(comment
  "how to use"
  (switch-to-page :profile)
  (switch-to-page :index)
  (get-active-page)
  )

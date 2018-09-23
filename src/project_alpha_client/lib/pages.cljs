
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the GNU General Public License
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
            [goog.Timer :as timer]
            [goog.style :as style]
            [local-settings :as setup])
  (:use [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.auth :only [clear-app-cookies]]))


(def ^{:doc "atom which holds the keyword of the currently acitve page"
       :private true }
  page (atom nil))


(defn reload-url
  "reloads the page (page is loaded from server)"
  [url]
  (js/eval (str "window.location.href='" url "';")))


(defn rewrite-url
  "only rewrite the url in the address line (page is already
   loaded and has been enabled only)"
  [url]
  (loginfo (str "url rewritten to: " url))
  (js/eval (str "window.history.pushState('', 'project-alpha', '" url "');")))


(defn get-lang-id
  "retrieves language id out of the second url section from right.
   This approach currently restricts to one level uri's. Later
   implementations might change this."
  []
  (let [url (js/eval (str "window.location.href"))]
    (second (reverse (re-seq #"[A-Za-z0-9:._=?%]+" url)))))


(defn switch-lang
  "switches the current language. deletes the cookies of the
   previously used language before which is necessary because
   otherwise login is impossible due to remaining cookie
   zombies."
  [lang]
  (let [new-url (str setup/base-url lang "/" (. (str @page) (substring 1)) ".html")]
    ;; (clear-app-cookies) ; not required anymore
    (reload-url new-url)))


(defn switch-to-page
  "switches to new page and rewrites the url
   This is emulating a new page load from server."
  [new-page]
  (let [new-url (str setup/base-url (get-lang-id) "/" (. (str new-page) (substring 1)) ".html")]
    (swap! page #(when (not= % new-page)
                   (loginfo (str "switched to page " new-page))
                   (rewrite-url new-url)
                   (dispatch/fire :page-switched {:from @page :to new-page})
                   new-page))))


(defn get-active-page
  "returns the currently active page"
  []
  @page)


(defn switch-to-page-deferred
  "like switch to page but defers execution
   by 10ms to enforce queued events to be
   processed."
  [new-page]
  (timer/callOnce #(switch-to-page new-page) 10))




(comment
  "how to use"
  (switch-to-page :profile)
  (switch-to-page :index)
  (get-active-page)
  )

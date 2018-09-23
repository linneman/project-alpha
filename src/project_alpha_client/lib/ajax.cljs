;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the GNU General Public License
;;;
;;; ====== functions for triggering AJAX POST and GET ======
;;;
;;; 2012-06-13, Otto Linnemann


(ns project-alpha-client.lib.ajax
  (:require [project-alpha-client.lib.json :as json]
            [project-alpha-client.lib.dispatch :as dispatch]
            [clojure.browser.event :as event]
            [clojure.browser.dom   :as dom]
            [goog.events :as events]
            [goog.events.EventType :as event-type]
            [goog.Timer :as timer]
            [local-settings :as setup])
  (:use [project-alpha-client.lib.pages :only [get-lang-id switch-to-page-deferred]]
        [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.auth :only [clear-app-cookies]]))


(defn- exp-url
  "expands method by language tag, later derived from URL"
  [url]
  (str setup/base-url (get-lang-id) url))


(defn- ajax-response-handler
  "ajax response handler which checks for errors before
   invoking the target handler"
  [error-handler target-handler]
  (fn [ajax-evt]
    (let [status (. (. ajax-evt -target) (getStatus))]
      (if (= status 403)
        (do (loginfo "Authentication Error!") (error-handler ajax-evt))
        (target-handler ajax-evt))
     )))


(defn send-request
  "send XHTTP request as string"
  ([url str] (send-request url str (fn [e] nil) "GET"))
  ([url str function] (send-request url str function "GET"))
  ([url str function method]
     (goog.net.XhrIo/send
      (exp-url url)
      (ajax-response-handler
       #(do (clear-app-cookies) (switch-to-page-deferred :index))
       function)
      method
      str
      (json/clj->js {"Content-Type" ["application/json"]}))))

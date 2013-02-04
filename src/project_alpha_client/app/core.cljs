;;;
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; ====== main-function ======
;;;
;;; 2011-11-23, Otto Linnemann


(ns project-alpha-client.app.core
  (:require [project-alpha-client.app.index :as index]
            [project-alpha-client.app.profile :as profile]
            [project-alpha-client.app.status :as status]
            [project-alpha-client.app.search :as search]
            [project-alpha-client.app.imprint :as imprint]
            [project-alpha-client.app.reset-password :as reset-password]
            [project-alpha-client.app.playground :as playground]
            [project-alpha-client.lib.pages :as pages]
            [project-alpha-client.lib.ajax :as ajax]
            [project-alpha-client.lib.repl :as repl]
            [project-alpha-client.app.repl :as replpage]
            )
  (:use [project-alpha-client.lib.logging :only [loginfo]]
        [project-alpha-client.lib.utils :only [current-url-keyword
                                               is-ios-device? show-elements-of-class]]))


(defn ^:export start
  "Start the application by switching to the index page"
  []
  (when (is-ios-device?)
    (show-elements-of-class "ios"))
  (pages/switch-to-page (current-url-keyword)))


;;; start the client side application
(start)

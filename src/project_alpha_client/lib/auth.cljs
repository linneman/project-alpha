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
;;; utility functions for checking authentification
;;;
;;; 2011-11-23, Otto Linnemann

(ns project-alpha-client.lib.auth
  (:require [goog.net.cookies :as cookies]))


(defn authenticated?
  "returns true when user has been registered
   and confirmed the registration"
  []
  (let [auth-cookie (. goog.net.cookies (get "authenticated"))]
    (= auth-cookie "true")))


(defn registered?
  "returns true when the user has been registered."
  []
  (let [auth-cookie (. goog.net.cookies (get "registered"))]
    (= auth-cookie "true")))

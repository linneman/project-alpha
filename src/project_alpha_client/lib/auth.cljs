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
  (:require [goog.net.cookies :as cookies]
            [goog.crypt.Sha1 :as sha1]
            [goog.crypt.base64 :as base64]))


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


(defn clear-app-cookies
  "removes cookies which is e.g. required for language change"
  []
  (. goog.net.cookies (remove "authenticated"))
  (. goog.net.cookies (remove "registered")))


(defn base64-sha1
  "computes base64 string of sha1 hash value of given string"
  [str]
  (let [sha1-obj (goog.crypt.Sha1.)
        str-array (js/eval "[]")
        str-map (doall
                 (map #(. str-array (push (. % (charCodeAt 0)))) str))]
    (. sha1-obj (update str-array))
    (base64/encodeByteArray (. sha1-obj (digest)))
    ))


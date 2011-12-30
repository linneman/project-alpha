;;; project-alpha (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; December 2011, Otto Linnemann


(ns project-alpha-server.auth
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :as compojure]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.middleware.json-params :as json-params]
            )
  (:use [compojure.core :only [GET POST PUT DELETE]]
        [ring.util.response :only [response]]
        [project-alpha-server.model]
        ))


(defn forward-url
  "utility function for forwarding to given url."
  [url]
  (format "<html><head><meta  http-equiv=\"refresh\" content=\"0; URL=%s\"></head><body>forwarding ...</body></html>" url))


(defn login
  "utility function for processing POST requests comming
   from login forms currently based on user name and password."
  [session name password login-get-uri]
  (if (check-user-password password name)
    (let [session (assoc session :authenticated "true")
          prev-req-uri (or (:prev-req-uri session) login-get-uri)]
      (-> (response (forward-url prev-req-uri)) (assoc :session session)))
    (response (forward-url login-get-uri))))


(defn logout
  "middleware for logout out

   This will removes the session cookie
   and later on forward to the start location."
  [session]
  (let [session (assoc session :name "")]
    (-> (response "<html><body><h1>logged out!</h1></body></html>") (assoc :session nil))))


(defn wrap-authentication
  "middleware for user authentication.

  The first arguemnt is the ring handler followed
  by the uri for requesting authentication data
  (username and password, open-id, etc.) and
  a whitelist of handlers which are not blocked.
  The login-get-uri is also excluded from blocking."
  [handler login-get-uri uri-white-list]
  (fn [request]
    (let [resp (handler request)
          uri (:uri request)
          session (:session request)
          authenticated (:authenticated session)
          uri-white-list (conj uri-white-list login-get-uri)
          upd-session (if (or (re-seq #"\.html$" uri)    ; remember uri only for html pages
                              (not (re-seq #"\/.*\..*$" uri))) ; or files without extentions
                        (assoc session :prev-req-uri uri) session)]
      (if (or (= "true" authenticated)
              (not (not-any? #(= 0 (compare % uri)) uri-white-list)))
        resp
        (-> (response (forward-url login-get-uri)) (assoc :session upd-session))))))

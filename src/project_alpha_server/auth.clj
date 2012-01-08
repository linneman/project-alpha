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
  [ring-args login-get-uri]
  (let [params (:params ring-args)
        session (:session ring-args)
        cookies (:cookies ring-args)
        name (params "name")
        password (params "password")]
    (if (check-user-password password name)
      (let [session (assoc session :authenticated true)
            cookies (assoc cookies "authenticated" {:value "true"})
            prev-req-uri (or (:prev-req-uri session) login-get-uri)]
        (-> (response (forward-url prev-req-uri))
            (assoc :session session)
            (assoc :cookies cookies)
            ))
      (response (forward-url login-get-uri)))))


(defn logout
  "middleware for logout out

   This will removes the session cookie
   and later on forward to the start location."
  [ring-args]
  (let [session (:session ring-args)
        cookies (:cookies ring-args)
        session (assoc session :authenticated false)
        cookies (assoc cookies "authenticated" {:value "false"})]
    (-> (response "<html><body><h1>logged out!</h1></body></html>")
        (assoc :session session) (assoc :cookies cookies))))


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
          uri-html? (fn [uri] (re-seq #"\.html$" uri))
          uri-json-request? (fn [uri] (not (re-seq #"\/.*\..*$" uri)))
          upd-session (if (or (uri-html? uri) (uri-json-request? uri))
                        (assoc session :prev-req-uri uri) ; don't remember css, img, etc.
                        session)]
      (if (or authenticated
              (and (not (uri-json-request? uri)) (not (uri-html? uri))) ; json and html are forbidden
              (not (not-any? #(re-seq (re-pattern (str "^" (.replace % "/" "\\/"))) uri) uri-white-list)))
        resp
        (-> (response (forward-url login-get-uri)) (assoc :session upd-session))))))

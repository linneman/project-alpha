;;; project-alpha (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; December 2011, Otto Linnemann


(ns project-alpha-server.lib.auth
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :as compojure]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.middleware.json-params :as json-params]
            [ring.util.codec :as codec]
            [project-alpha-server.local-settings :as setup])
  (:use [compojure.core :only [GET POST PUT DELETE]]
        [ring.util.response :only [response]]
        [project-alpha-server.lib.model]
        [project-alpha-server.lib.crypto :only [get-secret-key]]
        [project-alpha-server.lib.email :only [send-confirm-mail]]
        [swank.core :only [break]]))


(defn forward-url
  "utility function for forwarding to given url."
  [url]
  (format "<html><head><meta  http-equiv=\"refresh\" content=\"0; URL=%s\"></head><body>forwarding ...</body></html>" url))


(defn register
  "utility function for processing the POST request for
   user registration data. This triggers also the sending
   of the confirmation email."
  [ring-args]
  (let [params (:params ring-args)
        name (params "name")
        email (params "email")
        password (params "password")
        cat (fn [url method]
              (let [URL (java.net.URL. url)
                    host (.getHost URL)
                    port (.getPort URL)
                    protocol (.getProtocol URL)]
                (.toString (java.net.URL. protocol host port method))))
        key (codec/base64-encode (get-secret-key {}))
        confirmation_link (str "/confirm?key=" (codec/url-encode key))
        session (:session ring-args)
        cookies (:cookies ring-args)]
    (if (and (empty? (find-user-by-name name))
             (empty? (find-user-by-email email)))
      (do
        (add-user
         :name name
         :email email
         :password password
         :confirmation_link key)
        (if setup/email-authentication-required
          (let [session (assoc session :registered true)
                cookies (assoc cookies "registered" {:value "true"})]
            (send-confirm-mail email (cat setup/host-url confirmation_link))
            (-> (response "OK") (assoc :session session) (assoc :cookies cookies)))
          (let [session (assoc session :authenticated true)
                cookies (assoc cookies "authenticated" {:value "true"})]
            (-> (response "OK") (assoc :session session) (assoc :cookies cookies)))))
      (response "USER ALREADY REGISTERED, HACKER ACTIVITY?"))))


(defn confirm
  "invoked handler when user clicked email confirmation link"
  [ring-args url]
  (let [params (:params ring-args)
        key (:key params)
        user (find-user :confirmation_link key)
        session (:session ring-args)
        cookies (:cookies ring-args)]
    (if (not (empty? user))
      (let [session (assoc session :authenticated true)
            cookies (assoc cookies "authenticated" {:value "true"})]
        (update-user {:confirmed 1} {:confirmation_link key})
        (-> (response (forward-url url))
            (assoc :session session)
            (assoc :cookies cookies)
            ))
      (response "NOT OK"))))


(defn login
  "utility function for processing POST requests comming
   from login forms currently based on user name and password."
  [ring-args]
  (let [params (:params ring-args)
        session (:session ring-args)
        cookies (:cookies ring-args)
        name (params "name")
        password (params "password")
        user (check-user-password password name)]
    (if (and user (or (:confirmed user)
                      (not setup/email-authentication-required)))
      (let [session (assoc session :authenticated true)
            cookies (assoc cookies "authenticated" {:value "true"})]
        (-> (response "OK")
            (assoc :session session)
            (assoc :cookies cookies)
            ))
      (if user
        (response "NOT CONFIRMED")
        (response "NOT OK")))))


(defn logout
  "middleware for logout out

   This will removes the session cookie
   and later on forward to the start location."
  [ring-args]
  (let [session (:session ring-args)
        cookies (:cookies ring-args)
        session (assoc session :authenticated false)
        cookies (assoc cookies "authenticated" {:value "false"})]
    (-> (response "OK")
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
    (let [uri (:uri request)
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
        (handler request)
        (-> (response (forward-url login-get-uri)) (assoc :session upd-session))))))
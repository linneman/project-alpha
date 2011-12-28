;;; project-alpha (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; December 2011, Otto Linnemann


(ns project-alpha-server.core
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :as compojure]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.middleware.json-params :as json-params]
            )
  (:use [compojure.core :only [GET POST PUT DELETE]]
        [ring.util.response :only [response]]
        [ring.middleware.session :only [wrap-session]]
        [ring.middleware.multipart-params :only [wrap-multipart-params]]
        [project-alpha-server.model]
        ))


(defn session-counter [{session :session}]
  (let [count   (:count session 0)
        session (assoc session :count (inc count))]
    (-> (response (str "You accessed this page " count " times."))
        (assoc :session session))))


(defn forward-url [url]
  (format "<html><head><meta  http-equiv=\"refresh\" content=\"0; URL=%s\"></head><body>forwarding ...</body></html>" url))


(defn login [session name]
  (let [session (assoc session :name name)
        prev-req-uri (or (:prev-req-uri session) "/index.html")]
    (-> (response (forward-url prev-req-uri)) (assoc :session session))))


(defn logout [session]
  (let [session (assoc session :name "")]
    (-> (response "<html><body><h1>logged out!</h1></body></html>") (assoc :session nil))))


(defn wrap-authentification [handler login-uri]
  (fn [request]
    (let [resp (handler request)
          uri (:uri request)
          session (:session request)
          name (:name session)
          upd-session (if (re-seq #"\.html$" uri) (assoc session :prev-req-uri uri) session)]
      (if (and (not= uri login-uri) (not= name "otto"))
        (-> (response (forward-url "/login")) (assoc :session upd-session))
        resp))))


(compojure/defroutes main-routes
  (POST "/login" [name :as {session :session}] (login session name))
  (GET  "/login" _ "<form method='post' action='/login'> Login: <input type='text' name='name' /><input type='submit' /></form>")
  (GET "/logout" {session :session} (logout session))
  (GET "/status" _ "server-running")
  (GET "/session" args (str "<body>" args "</body>"))
  (GET "/counter" args (session-counter args))
  (POST "/profile" {params :params} (do (println (params "text")) "OK"))
  (route/resources "/")
  (route/not-found "Page not found"))

(def app
  (-> main-routes
      (wrap-authentification "/login")
      (wrap-session {:store (db-session-store) :cookie-attrs {:max-age (* 30 24 3600)}})
      json-params/wrap-json-params
      wrap-multipart-params
      handler/api))

(defonce server (jetty/run-jetty #'app
                           {:port 3000 :join? false}))

(defn -main [& args]
  (.start server)
  )

; (.start server)
; (.stop server)






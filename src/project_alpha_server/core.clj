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
        [project-alpha-server.auth]
        ))

(def ^{:private true
       :doc "login resource address for fetching user name and password"}
  login-get-uri
  "/login.html")

(def ^{:private true
       :doc "login resource address for posting user name and password"}
  login-post-uri
  "/login")

(defn session-counter
  "illustration how to use session (removed later on)"
  [{session :session}]
  (let [count   (:count session 0)
        session (assoc session :count (inc count))]
    (-> (response (str "You accessed this page " count " times."))
        (assoc :session session))))

(compojure/defroutes main-routes
  (POST login-post-uri [name password :as {session :session}]
        (login session name password login-get-uri))
  (GET "/logout" {session :session} (logout session))
  (GET "/status" _ "server-running")
  (GET "/session" args (str "<body>" args "</body>"))
  (GET "/counter" args (session-counter args))
  (POST "/profile" {params :params} (do (println (params "text")) "OK"))
  (route/resources "/")
  (route/not-found "Page not found"))

(def app
  (-> main-routes
      (wrap-authentication login-get-uri [login-post-uri])
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








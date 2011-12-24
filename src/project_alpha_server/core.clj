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
  (:use [ring.util.response :only [response]]))

(defn session-counter [{session :session}]
  (let [count   (:count session 0)
        session (assoc session :count (inc count))]
    (-> (response (str "You accessed this page " count " times."))
        (assoc :session session))))

(compojure/defroutes main-routes
    (compojure/GET "/status" _ "server-running")
    (compojure/GET "/session" args (str "<body>" args "</body>"))
    (compojure/GET "/login" args (session-counter args))
    (compojure/POST "/profile" {params :params} (do (println (params "text")) "OK"))
    (route/resources "/")
    (route/not-found "Page not found")
    )

(def app
  (-> main-routes
      json-params/wrap-json-params
      handler/site
      ))

(defonce server (jetty/run-jetty #'app
                           {:port 3000 :join? false}))

(defn -main [& args]
  (.start server)
  )

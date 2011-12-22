;;; project-alpha (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; December 2011, Otto Linnemann


(ns project-alpha-server.core
    (:require [ring.adapter.jetty :as jetty])
    (:require [compojure.core :as compojure])
    (:require [compojure.route :as route])
    (:require [ring.middleware.json-params :as json-params])
    )

(compojure/defroutes app-routes
    (compojure/GET  "/status" _ "server-running")
    (compojure/POST "/profile" {params :params} (do (println (params "text")) "OK"))
    (route/files "/")
    )

(def routes
  (-> app-routes
      json-params/wrap-json-params
      ))

(defonce server (jetty/run-jetty #'routes
                           {:port 3000 :join? false}))

(defn -main [& args]
  (.start server)
  )

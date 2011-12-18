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
    )

(compojure/defroutes app-routes
    (compojure/GET   "/status" _ "server-running")
    (route/files "/")
    )


(defonce server (jetty/run-jetty #'app-routes
                           {:port 3000 :join? false}))

(defn -main [& args]
  (.start server)
  )

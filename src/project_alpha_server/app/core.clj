;;; project-alpha (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; December 2011, Otto Linnemann


(ns project-alpha-server.app.core
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.json-params :as json-params]
            [compojure.core :as compojure]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [net.cgrand.enlive-html :as html]
            )
  (:use [compojure.core :only [GET POST PUT DELETE]]
        [ring.util.response :only [response]]
        [ring.util.codec :only [url-decode url-encode]]
        [ring.middleware.session :only [wrap-session]]
        [ring.middleware.cookies :only [wrap-cookies]]
        [ring.middleware.multipart-params :only [wrap-multipart-params]]
        [project-alpha-server.lib.model]
        [project-alpha-server.lib.auth]
        [clojure.data.json :only [json-str write-json read-json]]
        [clojure.pprint :only [pprint]]
        ))


(def ^{:private true
       :doc "path to html resources"}
  templates-path
  "resources/templates")

(def ^{:private true
       :doc "outer frame used for layout"}
  layout-resource
  "layout.html")

(def ^{:private true
       :doc "login resource address for fetching user name and password"}
  login-get-uri
  "/index.html")

(def ^{:private true
       :doc "login resource address for posting user name and password"}
  login-post-uri
  "/login")

(def ^{:private true
       :doc "register resource address for fetching registration data"}
  register-get-uri
  "/register.html")

(def ^{:private true
       :doc "register resource address for post registration data"}
  register-post-uri
  "/register")

(defn log-request-handler
  [handler]
  (fn [request]
    (let [response (handler request)]
      (do (println response) response))))

(defn session-counter
  "illustration how to use session (removed later on)"
  [{session :session}]
  (let [count   (:count session 0)
        session (assoc session :count (inc count))]
    (-> (response (str "You accessed this page " count " times."))
        (assoc :session session))))

(defn gen-site
  "takes enclosing html side and replaces specified
   element by concatenated content-pages."
  [frame-page elem-to-replace & content-pages]
  (let [index (html/html-resource frame-page)
        repl-content
        (mapcat
         #(:content (first (:content (first (html/html-resource %)))))
         content-pages)
        result (html/transform index elem-to-replace (fn [_] repl-content))]
    (apply str (html/emit* result))))

(defn site
  "shortcut for gen-site which appends <templates-path>
   to frame and content pages and uses the div element
   'content_pane' to be replaced."
  [& sites]
  (let [cat (fn [path name]
              (.toString (java.io.File. path name)))
        frame layout-resource
        full-name-frame (cat templates-path frame)
        full-name-sites (map #(cat templates-path %) sites)
        ]
    (apply (partial gen-site full-name-frame [:div#content_pane])
           full-name-sites)))

(defn user-response
  "used for ensuring that user name is unique"
  [name]
  (let [[data] (find-user-by-name-or-email name)]
    ;(println "requested user: " name)
    (if (not-empty data)
      (json-str (select-keys data [:name :email :id :level :confirmed]))
      (json-str {}))))

(compojure/defroutes main-routes
  ;; --- authentification and registration ---
  (POST login-post-uri args (login args))
  (POST "/logout" args (logout args))
  (POST register-post-uri args (register args))
  (GET ["/user/:name" :name #".*"] [name] (let [name (url-decode name)] (user-response name)))
  ;; --- static html (composed out of outer layout side and inner content pane ---
  (GET "/index.html" _ (site "register.html" "login.html" "index.html"))
  (GET "/profile.html" _ (site "profile.html"))
  (GET "/register.html" _ (site "register.html"))
  (GET "/confirm" args (confirm args "index.html"))
  ;; --- json handlers ---
  (GET "/status" _ "server-running")
  (GET "/session" args (str "<body>" args "</body>"))
  (GET "/counter" args (session-counter args))
  (POST "/profile" {params :params session :session} (do (println (params "text")) "OK"))
  (route/resources "/")
  (route/not-found "Page not found"))

(def app
  (-> main-routes
      (wrap-authentication login-get-uri [login-post-uri register-get-uri register-post-uri "/user" "/confirm"])
      (wrap-session {:store (db-session-store) :cookie-attrs {:max-age (* 30 24 3600)}})
      wrap-cookies
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
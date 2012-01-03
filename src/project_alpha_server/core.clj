;;; project-alpha (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; December 2011, Otto Linnemann


(ns project-alpha-server.core
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
        [ring.middleware.multipart-params :only [wrap-multipart-params]]
        [project-alpha-server.model]
        [project-alpha-server.auth]
        [clojure.data.json :only [json-str write-json read-json]]
        [clojure.pprint :only [pprint]]
        ))


(def ^{:private true
       :doc "path to html resources"}
  resource-path
  "resources/public")

(def ^{:private true
       :doc "login resource address for fetching user name and password"}
  login-get-uri
  "/login.html")

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

(defn session-counter
  "illustration how to use session (removed later on)"
  [{session :session}]
  (let [count   (:count session 0)
        session (assoc session :count (inc count))]
    (-> (response (str "You accessed this page " count " times."))
        (assoc :session session))))

(defn gen-site
  "takes enclosing html side and replaces specified
   element by content-page."
  [frame-page elem-to-replace content-page]
  (let [index (html/html-resource frame-page)
        replacement-side (html/html-resource content-page)
        replacement-content (:content (first (:content (first replacement-side))))
        result (html/transform index elem-to-replace (fn [_] replacement-content))]
    (apply str (html/emit* result))))

(defn site
  "shortcut for gen-site which appends <resource-path>
     to frame and content page and uses the div element
     'content_pane' to be replaced."
  ([name] (site name "layout.html"))
  ([name frame]
     (let [cat (fn [path name]
                 (.toString (java.io.File. path name)))
           full-site-name (cat resource-path name)
           full-frame-name (cat resource-path frame)]
       (gen-site full-frame-name [:div#content_pane] full-site-name)
       )))

(defn user-response [name]
  (let [[data] (find-user-by-name-or-email name)]
    (println "requested user: " name)
    (if (not-empty data)
      (json-str (select-keys data [:name :email :id :level :confirmed]))
      (json-str {}))))

(compojure/defroutes main-routes
  (POST login-post-uri [name password :as {session :session}]
        (login session name password login-get-uri))
  ;; --- authentification and registration ---
  (GET "/logout" {session :session} (logout session))
  (GET ["/user/:name" :name #".*"] [name] (let [name (url-decode name)] (user-response name)))
  ;; --- static html (composed out of outer layout side and inner content pane ---
  (GET "/index.html" _ (site "index.html"))
  (GET "/register.html" _ (site "register.html"))
  ;; --- json handlers ---
  (GET "/status" _ "server-running")
  (GET "/session" args (str "<body>" args "</body>"))
  (GET "/counter" args (session-counter args))
  (POST "/profile" {params :params} (do (println (params "text")) "OK"))
  (route/resources "/")
  (route/not-found "Page not found"))

(def app
  (-> main-routes
      (wrap-authentication login-get-uri [login-post-uri register-get-uri register-post-uri "/user"])
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

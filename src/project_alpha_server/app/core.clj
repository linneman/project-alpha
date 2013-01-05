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
            [project-alpha-server.local-settings :as setup]
            [swank.swank])
  (:use [clojure.string :only [split]]
        [compojure.core :only [GET POST PUT DELETE]]
        [ring.util.response :only [response]]
        [ring.util.codec :only [url-decode url-encode]]
        [ring.middleware.session :only [wrap-session]]
        [ring.middleware.cookies :only [wrap-cookies]]
        [ring.middleware.multipart-params :only [wrap-multipart-params]]
        [project-alpha-server.app.escape-handlers]
        [project-alpha-server.lib.model]
        [project-alpha-server.app.model]
        [project-alpha-server.app.tests :only (create-test-users)]
        [project-alpha-server.lib.auth]
        [project-alpha-server.lib.utils]
        [project-alpha-server.app.find-users]
        [project-alpha-server.lib.rewrite]
        [clojure.pprint :only [pprint]]
        [clojure.data.json :only [json-str write-json read-json]]
        [cljs.repl :only (repl)]
        [cljs.repl.browser :only (repl-env)]
        [project-alpha-server.lib.crypto :only (base64-sha1)]
        [macros.macros]))


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

(def ^{:private true
       :doc "list of handlers which are not bloacked by authentication"}
  white-list-handlers
  ["/" login-post-uri register-get-uri register-post-uri
   "/user" "/confirm" "/reset_pw_req" "/reset_pw_conf"])


(defn log-request-handler
  "simple logger (debugging purposes)"
  [handler]
  (fn [request]
    (let [response (handler request)]
      (println (str "REQUEST -> " (request :uri)))
      ;(println response)
      response)))


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
  [lang & sites]
  (let [templates-path (str templates-path "/" lang)
        cat (fn [path name]
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

(defn- profile-resp-for
  "delivers ajax response for profile request for given id"
  [id]
  (let [prof (dissoc (get-profile id) :modified)
        name ((first (find-user-by-id id)) :name)]
    (if prof
      (json-str (merge prof {:name name}))
      (json-str "cleaned"))))


(def ^{:private true :doc "provides templates which are used for all sites"}
  standard-pages
  ["login.html" "nav.html" "index.html" "status.html" "profile.html" "search.html"
   "user_details_dialog.html" "imprint.html"])


;; --- application routes ---

(compojure/defroutes main-routes
  ;; --- authentification and registration ---
  (POST login-post-uri args (login args))
  (POST "/logout" args (logout args))
  (POST register-post-uri args (register args))
  (POST "/reset_pw_req" args (reset-pw-req args))
  (POST "/set_password" args (set-password args))
  (GET ["/user/:name" :name #".*"] [name] (let [name (url-decode name)] (user-response name)))
  ; --- static html routes ---
  (GET "/:lang/test.html" [lang] (do (println lang) "OK"))
  (GET "/:lang/index.html" [lang] (apply site lang "register.html" standard-pages))
  (GET "/:lang/status.html" [lang] (apply site lang standard-pages))
  (GET "/:lang/profile.html" [lang] (apply site lang standard-pages))
  (GET "/:lang/search.html" [lang] (apply site lang standard-pages))
  (GET "/:lang/mprint.html" [lang] (apply site lang standard-pages))
  (GET "/:lang/reset_pw.html" [lang] (apply site lang "register.html" "reset_pw.html" standard-pages))
  ;; --- json handlers ---
  (GET "/clear-session" args (-> (response (forward-url (str "/" setup/default-language "/index.html")))
               (assoc :session "")
               (assoc :cookies "")))
  (GET "/status" _ "server-running")
  (GET "/confirm" args (confirm args (str "/" (args :lang) "/profile.html")))
  (GET "/reset_pw_conf" args (confirm args (str "/" (args :lang) "/reset_pw.html")))
  (GET "/session" args (str "<body>" args "</body>"))
  (GET "/counter" args (session-counter args))
  (POST "/profile" {params :params session :session} (do (println (json2clj-hash params)) (update-profile (:id session) (json2clj-hash params)) "OK"))
  (GET "/profile" {session :session} (profile-resp-for (:id session)))
  (GET "/profile/:id" {params :route-params} (profile-resp-for (Integer/parseInt (:id params))))
  (POST "/flush-profile" {session :session} (json-str (flush-profile (:id session))))
  (POST "/delete-all-profile-data" {session :session} (delete-all-user-data (:id session)))
  (GET "/user-matches" {session :session} (json-str (find-all-matches :user-id (:id session))))
  (GET "/user-favorites" {session :session} (json-str (find-all-favorites :user-id (:id session))))
  (GET "/user-fav-user-ids" {session :session} (json-str (get-all-fav-users-of (:id session))))
  (POST "/add-fav-user" {params :params session :session} (add-fav-user :user_id (:id session) :match_id (params "match_id")) "OK")
  (POST "/del-fav-user" {params :params session :session} (delete-fav-user :user_id (:id session) :match_id (params "match_id")) "OK")
  (POST "/new-message" {session :session params :params}  (apply-hash (merge {:sender-id (:id session)} (json2clj-hash params)) new-message))
  (GET "/correspondence/:id" {session :session params :route-params} (json-str (get-correspondence (:id session) (Integer/parseInt (:id params)))))
  (GET "/read-messages" {session :session params :params} (json-str (get-read-messages (:id session))))
  (GET "/unread-messages" {session :session params :params} (json-str (get-unread-messages (:id session))))
  (GET "/unread-messages-sha1" {session :session params :params} (base64-sha1 (json-str (get-unread-messages (:id session)))))
  (GET "/unanswered-messages" {session :session params :params} (json-str (get-unanswered-messages (:id session))))
  (GET "/" _ (forward-url (str "/" setup/default-language "/index.html")))
  (route/resources "/")
  (route/not-found "Page not found"))


(def app
  (-> main-routes
      anti-xss-handler
      ;log-request-handler
      (wrap-authentication login-get-uri white-list-handlers)
      (wrap-session {:store (db-session-store) :cookie-attrs {:max-age setup/cookie-max-age}})
      rewrite-handler
      json-params/wrap-json-params
      wrap-multipart-params
      handler/api))


(defn start-server
  "starts the websever"
  []
  ;;; start the profile cache flush timer
  (start-profile-flush-cache-timer 60000)

  (defonce server (jetty/run-jetty #'app
                                 {:port 3000 :join? false}))
  (.start server))


(defn stop-server
  "stop the webserver"
  []
  (.stop server)
  (stop-profile-flush-cache-timer)
  )


(defn cljs-repl
  "starts up the clojurescript repl"
  []
  (repl (repl-env)))


(defn create-all-tables
  "creates all application specific tables.
   note: the open geo data base needs to
         be initialized separately."
  []
  (create-users)
  (create-user-fav-users)
  (create-sessions)
  (create-profiles)
  (create-books)
  (create-user-fav-books)
  (create-movies)
  (create-user-fav-movies)
  (create-messages)
  (create-unread-messages)
  )


(defn -main [& args]
  (swank.swank/start-server :port 4005)
  (start-server)
  )

; (start-server)
; (stop-server)

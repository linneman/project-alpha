;;; project-alpha (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the GNU General Public License
;;;
;;; December 2011, Otto Linnemann


(ns project-alpha-server.app.core
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.json-params :as json-params]
            [compojure.core :as compojure]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [net.cgrand.enlive-html :as html]
            [local-settings :as setup]
            [swank.swank])
  (:use [clojure.string :only [split replace-first]]
        [compojure.core :only [GET POST PUT DELETE]]
        [ring.util.response :only [response content-type charset]]
        [ring.util.codec :only [url-decode url-encode]]
        [ring.middleware.session :only [wrap-session]]
        [ring.middleware.cookies :only [wrap-cookies]]
        [ring.middleware.multipart-params :only [wrap-multipart-params]]
        [ring.middleware.resource]
        [ring.middleware.file-info]
        [project-alpha-server.app.escape-handlers]
        [project-alpha-server.lib.model]
        [project-alpha-server.app.model]
        [project-alpha-server.app.tests :only (create-test-users)]
        [project-alpha-server.lib.auth]
        [project-alpha-server.lib.utils]
        [project-alpha-server.app.find-users]
        [project-alpha-server.app.email-notification]
        [project-alpha-server.lib.rewrite]
        [clojure.pprint :only [pprint]]
        [clojure.data.json :only [json-str write-json read-json]]
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
       :doc "list of handlers which are not blocked by authentication"}
  white-list-handlers
  ["/" login-get-uri login-post-uri register-get-uri register-post-uri
   "/user" "/confirm" "/reset_pw_req" "/reset_pw_conf" "/repl.html"])


(defn log-request-handler
  "simple logger (debugging purposes)"
  [handler]
  (fn [request]
    (let [response (handler request)]
      (println (str "REQUEST -> URI: " (request :uri)))
      (println (str "REQUEST -> HEADERS:\n" (request :headers)))
      ;(println "RESPONSE:\n")
      ;(println response)
      (println "_________________________")
      response)))


(defn wrap-uri-prefix
  "removes uri prefix aka base url from :uri tag"
  [handler prefix]
  (fn [request]
    (handler (assoc request
                    :uri (replace-first (:uri request)
                                        (re-pattern (str "^" prefix "/?"))
                                        "/")))))


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

(defn static-site
  "returns static html file used e.g. for out of order respectively
   browser not supported messages."
  [lang name]
  (let [templates-path (str templates-path "/" lang)
        fullname (.toString (java.io.File. templates-path name))
        res (html/html-resource fullname)]
    (apply str (html/emit* res))))

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
  (let [prof (dissoc (get-profile id) :modified :last_seek)
        name ((first (find-user-by-id id)) :name)]
    (if prof
      (json-str (merge prof {:name name}))
      (json-str "cleaned"))))


(def ^{:private true :doc "provides templates which are used for all sites"}
  standard-pages
  ["login.html" "nav.html" "index.html" "status.html" "profile.html" "search.html"
   "user_details_dialog.html" "imprint.html"])

(def ^{:private true :doc "provides optimized release scripts"}
  release-scripts
  ["release_scripts.html"])

(def ^{:private true :doc "provides scripts for debugging via repl"}
  debug-scripts
  ["debug_scripts.html"])

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
  (GET "/:lang/index.html" [lang] (apply site lang "register.html" (concat standard-pages release-scripts)))
  (GET "/:lang/status.html" [lang] (apply site lang (concat standard-pages release-scripts)))
  (GET "/:lang/profile.html" [lang] (apply site lang (concat standard-pages release-scripts)))
  (GET "/:lang/search.html" [lang] (apply site lang (concat standard-pages release-scripts)))
  (GET "/:lang/imprint.html" [lang] (apply site lang (concat standard-pages release-scripts)))
  (GET "/:lang/reset_pw.html" [lang] (apply site lang "register.html" "reset_pw.html" (concat standard-pages release-scripts)))
  (GET "/:lang/repl.html" [lang] (apply site lang "register.html" "reset_pw.html" (concat standard-pages debug-scripts)))
  ;; --- json handlers ---
  (GET "/clear-session" args (-> (response (forward-url (str setup/host-url setup/default-language "/index.html")))
               (assoc :session "")
               (assoc :cookies "")))
  (GET "/status" _ "server-running")
  (GET "/confirm" args (confirm args (str setup/host-url (args :lang) "/profile.html")))
  (GET "/reset_pw_conf" args (confirm args (str setup/host-url (args :lang) "/reset_pw.html")))
  (GET "/session" args (str "<body>" args "</body>"))
  (GET "/counter" args (session-counter args))

  (POST "/profile" {params :params session :session} (do (println (json2clj-hash params)) (update-profile (:id session) (json2clj-hash params)) "OK"))
  (GET "/profile" {session :session} (profile-resp-for (:id session)))
  (GET "/profile/:id" {params :route-params} (profile-resp-for (Integer/parseInt (:id params))))
  (POST "/flush-profile" {session :session} (json-str (flush-profile (:id session))))
  (POST "/check-profile" {session :session} (json-str (check-profile (:id session))))
  (POST "/delete-all-profile-data" {session :session} (delete-all-user-data (:id session)))
  (GET "/user-matches" {session :session} (json-str (find-all-matches :user-id (:id session))))

  (GET "/user-favorites" {session :session} (json-str (find-all-favorites :user-id (:id session))))
  (GET "/user-fav-user-ids" {session :session} (json-str (get-all-fav-users-of (:id session))))
  (POST "/add-fav-user" {params :params session :session} (add-fav-user :user_id (:id session) :match_id (params "match_id")) "OK")
  (POST "/del-fav-user" {params :params session :session} (delete-fav-user :user_id (:id session) :match_id (params "match_id")) "OK")

  (GET "/user-banned" {session :session} (json-str (find-all-banned :user-id (:id session))))
  (GET "/user-banned-user-ids" {session :session} (json-str (get-all-banned-users-of (:id session))))
  (POST "/add-banned-user" {params :params session :session} (add-banned-user :user_id (:id session) :match_id (params "match_id")) "OK")
  (POST "/del-banned-user" {params :params session :session} (delete-banned-user :user_id (:id session) :match_id (params "match_id")) "OK")

  (POST "/new-message" {session :session params :params}  (apply-hash (merge {:sender-id (:id session)} (json2clj-hash params)) new-message))
  (GET "/correspondence/:id" {session :session params :route-params} (json-str (get-correspondence (:id session) (Integer/parseInt (:id params)))))
  (GET "/read-messages" {session :session params :params} (json-str (get-read-messages (:id session))))
  (GET "/unread-messages" {session :session params :params} (json-str (get-unread-messages (:id session))))
  (GET "/unread-messages-sha1" {session :session params :params} (base64-sha1 (json-str (get-unread-messages (:id session)))))
  (GET "/unanswered-messages" {session :session params :params} (json-str (get-unanswered-messages (:id session))))
  (GET "/" args (do (println "") (forward-url (str setup/host-url (:lang args) "/index.html"))))
  (route/resources "/")
  (route/not-found "Page not found"))


(defn not-supported-handler
  "delivered special pages for not supported browsers (IE)
   or when site is in maintenance mode."
  [handler]
  (fn [request]
    (let [headers (request :headers)
          lang (. (or (headers "accept-language") setup/default-language)  substring 0 2)
          lang (or (setup/languages lang) setup/default-language)
          agent (headers "user-agent")
          is-ie-agent (re-seq #"(?i).*(msie).*" agent)]
      (if (re-seq #"(\/|html)$" (request :uri)) ; filter out only html and /
        (if is-ie-agent
          (-> (response (static-site lang "not_supported.html"))
              (content-type "text/html") (charset "utf-8"))
          (if setup/maintencance-mode
            (-> (response (static-site lang "in_maintenance.html"))
                (content-type "text/html") (charset "utf-8"))
            (handler request)))
        (handler request)))))


(defn save-lang-handler
  "we need to persistently store the preferred language to
   be able to deliver email according to the last adjusted
   language. Since the language can only be changed in the
   main page (index.html) we have to catch only GET request
   for this, retrieve the current language tag and update
   the profiles table with it."
  [handler]
  (fn [request]
    (let [uri (:uri request)
          lang (:lang request)
          session (:session request)
          id (:id session)]
      (when (and (re-seq #"index\.html$" uri) lang id)
        (println "updating prefered languages to" (:lang request) "for user" id)
        (update-profile-lang id lang)))
    (handler request)))


(def app
  (-> main-routes
      save-lang-handler
      anti-xss-handler
      (wrap-authentication login-get-uri white-list-handlers)
      (wrap-session {:store (db-session-store)
                     :cookie-attrs {:max-age setup/cookie-max-age}})
      rewrite-handler
      not-supported-handler
      json-params/wrap-json-params
      wrap-multipart-params
      (wrap-resource "public")
      (wrap-file-info)
      handler/api
      (wrap-uri-prefix "/project-alpha")
      ;log-request-handler
      ))


(defn disable-sslv3
  [server]
  (doseq [c (.getConnectors server)]
    (when (instance? org.eclipse.jetty.server.ssl.SslSelectChannelConnector c)
      (let [f (.getSslContextFactory c)]
        (.addExcludeProtocols f (into-array ["SSLv2Hello" "SSLv3"]))))
  server))


(defn start-server
  "starts the websever"
  []
  ;;; start the profile cache flush timer
  (start-profile-flush-cache-timer 60000)
  (start-email-notification-timer)
  (let [jetty-setup (merge {:configurator disable-sslv3} setup/jetty-setup)]
   (defonce server (jetty/run-jetty #'app
                                   jetty-setup)))
  (.start server))

(comment
  (.stop server)
  (let [jetty-setup (merge {:configurator disable-sslv3} setup/jetty-setup)]
    (def server (jetty/run-jetty #'app jetty-setup)))
  (.start server)

  )

(defn stop-server
  "stop the webserver"
  []
  (.stop server)
  (stop-profile-flush-cache-timer)
  (stop-email-notification-timer)
  )


(defn create-all-tables
  "creates all application specific tables.
   note: the open geo data base needs to
         be initialized separately."
  []
  (create-users)
  (create-user-fav-users)
  (create-user-banned-users)
  (create-sessions)
  (create-profiles)
  (create-books)
  (create-user-fav-books)
  (create-movies)
  (create-user-fav-movies)
  (create-messages)
  (create-unread-messages)
  (create-email-notification-table)
  )


(defn -main [& args]
  (swank.swank/start-server :port 4005)
  (start-server)
  )

; (start-server)
; (stop-server)

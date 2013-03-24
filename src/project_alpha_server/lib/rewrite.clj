;;; project-alpha (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the GNU General Public License
;;;
;;; June 2012, Otto Linnemann
;;;
;;; uri rewrite handler for language prefix


(ns project-alpha-server.lib.rewrite
  (:require [project-alpha-server.local-settings :as setup])
  (:use [clojure.string :only [split]]
        [project-alpha-server.lib.utils]))

(defn rewrite-handler
  "Extracts language prefix from given request and remove
   this prefix for all language independent requests such
   as e.g. css, jpeg, js and ajax handlers. Currently html
   pages are the only language dependent resources.

   Puts appropriate :lang key to request to allow language
   dependent implementation in subsequent handlers e.g.
   email sending for different languages.

   If no language prefix is given set language to default
   language in redirect to this url in case of an html
   request.

   Please refer also to the invocation examples below."
  [handler]
  (fn [request]
    (let [uri (request :uri)
          headers (request :headers)
          accept-lang (. (headers "accept-language") substring 0 2)
          accept-lang (or (setup/languages accept-lang) setup/default-language)
          [[_ ext]] (re-seq #"\.([a-zA-Z0-9\-]+)$" uri)
          [[_ lang]] (re-seq #"^\/([a-z]+)\/" uri)
          uri-def-lang (setup/languages lang)
          lang (or uri-def-lang accept-lang)
          request (if (= ext "html")
                    (if uri-def-lang ; only html remains lang-attribute
                      (assoc request :lang lang)
                      (assoc request :uri (str "/" lang uri) :lang lang))
                    (if uri-def-lang ; if lang attributes is given, remove it
                      (assoc request :uri (str "/" (last (split uri #"\/" 3))) :lang lang)
                      (assoc request :lang lang)))]
      (if (and (not uri-def-lang) (= ext "html"))
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (forward-url (request :uri))}
        (handler request)))))


(comment usage-illustration

         ((rewrite-handler identity) {:uri "/de/search/user/13/profile.html"})
         ((rewrite-handler identity) {:uri "/en/search/user/13/profile.html"})
         ((rewrite-handler identity) {:uri "/search/user/13/profile.html"})
         ((rewrite-handler identity) {:uri "/de/search/user/13/profile"})

         ((rewrite-handler identity) {:uri "/de/search.html"})
         ((rewrite-handler identity) {:uri "/en/index.html"})
         ((rewrite-handler identity) {:uri "/search.css"})
         ((rewrite-handler identity) {:uri "/de/search.css"})

         ((rewrite-handler identity) {:uri "/de/login"})
         ((rewrite-handler identity) {:uri "/login"})
         ((rewrite-handler identity) {:uri "/login"})
         ((rewrite-handler identity) {:uri "/profile"})


         )

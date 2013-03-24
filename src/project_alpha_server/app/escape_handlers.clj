;;; project-alpha (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the GNU General Public License
;;;
;;; May 2012, Otto Linnemann


(ns project-alpha-server.app.escape-handlers)


(defn- check-image
  "checks images separately"
  [img-html]
  (-> img-html
      (.replaceAll "eval\\((.*)\\)"  "")
      (.replaceAll "((?i)script)"  "")
      (.replaceAll "\\.js" "")))


(defn- filter-out-xss
  "if argument is a string filter out everything suspicious
   to belong to active javascript content in order to avoid
   xss attacks."
  [arg]
  (if (string? arg)
    (let [image-list (re-seq #"<img[^>]*>" arg)
          no-image-str (-> arg
                           (.replaceAll "<img[^>]*>" "")
                           (.replaceAll "eval\\((.*)\\)"  "")
                           (.replaceAll "((?i)script)"  ""))]
      (apply str
             (concat (doall (map check-image image-list))
                     (list no-image-str))))
    arg))


(comment
  "usage illustration"

  (filter-out-xss
   "hello world <img src=\"http://somewhere/something.jpg\"> abc <img src=\"bad.js\">")

  )


(defn anti-xss-handler
  "cleans out all suspicious argument strings (javascript)
   to avoid xss attacks."
  [handler]
  (fn [request]
    (def my (request :params))
    (let [params (request :params)
          params (apply hash-map (interleave
                                  (keys params)
                                  (map filter-out-xss (vals params))))
          request (assoc request :params params)]
      (let [response (handler request)]
        response))))

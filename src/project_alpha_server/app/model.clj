;;; project-alpha (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; December 2011, Otto Linnemann

;;; example used from the net, many thanks to:
;;; http://en.wikibooks.org/wiki/Clojure_Programming/Examples/JDBC_Examples
;;; http://sqlkorma.com
;;; https://gist.github.com/1521214

(ns project-alpha-server.app.model
  (:require [korma.db :as db]
            [korma.core :as sql]
            [clojure.java.jdbc :as jdbc]
            [ring.util.codec :as codec]
            [project-alpha-server.local-settings :as setup])
  (:use [project-alpha-server.lib.model]
        [ring.middleware.session.store :only [SessionStore]]
        [project-alpha-server.lib.crypto :only
         [get-secret-key get-encrypt-pass-and-salt decrypt-pass]])
  (:import [java.text DateFormat SimpleDateFormat]
           [java.util TimerTask Timer]))



;; --- table creation and deletion ---


;; --- profiles ---

(defn create-profiles
  "Create user table"
  []
  (create-table
   :profiles
   [:id :integer "PRIMARY KEY"]
   [:text "text"]
   [:user_sex "varchar(12)"]
   [:user_interest_sex "varchar(12)"]
   [:user_age :integer]
   [:user_zip "varchar(5)"]
   [:user_lat :double]
   [:user_lon :double]
   [:modified "timestamp"]
   ))


(defn drop-profiles
  []
  "Delete users table"
  (drop-table :profiles))

(comment usage illustration

  (create-profiles)
  (drop-profiles))




;; --- information retrieval ---

; --- profiles ( internals ) ---

(sql/defentity profiles)
(def profile-cache (atom {}))


(defn- flush-profile-cache
  "flushes the profile cache. This function is
   frequently invoked by a timer."
  [h]
  (doseq [[id fields] h]
    (insert-or-update-when-not-modified profiles id fields)))


(defn- start-profile-flush-cache-timer
  "starts a timer which writes back the cached data
   for users profile after the specified timer in seconds."
  [period]
  (let [task (proxy [TimerTask] []
               (run []
                 (swap! profile-cache flush-profile-cache)
                 (println "profile cache flushed")))]
    (def profile-flush-timer (new Timer))
    (. profile-flush-timer
       (schedule task (long period) (long period)))))


(defn- stop-profile-flush-cache-timer
  []
  "stops flushing the profile cache"
  (. profile-flush-timer (cancel)))


;;; start the profile cache flush timer
(start-profile-flush-cache-timer 60000)



; --- profiles public API ---


(defn update-profile
  "queues new profile data for db storage"
  [id fields]
  (swap! profile-cache
         #(let [merged-fields (if % (merge (% id) fields) fields)
                mf-and-modified (assoc merged-fields
                                  :modified (java.util.Date.))]
            {id mf-and-modified})))


(defn find-profile
  "finds profile with given keys in db.
   for request to update user profile data
   use get-profile instead in order to
   take into account the cache."
  [& {:as args}]
  (sql/select profiles (sql/where args)))


(defn get-profile
  "updates cache and retrieves profile data
   for given id."
  [id]
  (swap! profile-cache flush-profile-cache)
  (first (find-profile :id id)))


(defn delete-profile
  "deletes profile data for a given key"
  [& {:as args}]
  (sql/delete profiles (sql/where args)))


(comment
  usage illustration

  (update-profile 2 {:text "ABC"})
  (update-profile 3 {:text "Markus Linnemann"})
  (update-profile 3 {:text "Vorderster Linnemann"})
  (update-profile 3 {:text "Otto Linnemann"})
  (update-profile 4 {:text "Konrad Linnemann"})

  (def a (get-profile 78))
  (delete-profile :id 4)
  (swap! profile-cache flush-profile-cache)

  (start-profile-flush-cache-timer 60000)
  (stop-profile-flush-cache-timer)

  )


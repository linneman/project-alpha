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
         [get-secret-key get-encrypt-pass-and-salt decrypt-pass]]
        [clojure.data.json :only [json-str write-json read-json]]
        [project-alpha-server.lib.utils])
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
   [:question_1 :tinyint]
   [:question_2 :tinyint]
   [:question_3 :tinyint]
   [:question_4 :tinyint]
   [:question_5 :tinyint]
   [:question_6 :tinyint]
   [:question_7 :tinyint]
   [:question_8 :tinyint]
   [:question_9 :tinyint]
   [:question_10 :tinyint]
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


(defn create-books
  "create favourite book list"
  []
  (create-table
   :books
   [:id :integer "PRIMARY KEY" "AUTO_INCREMENT"]
   [:author "varchar(255)"]
   [:title "varchar(255)"]
   [:isbn "varchar(13)"]))

(defn drop-books
  "delete fav book table"
  []
  (drop-table :books))

(defn create-user-fav-books
  "creates relation table user-fav-books"
  []
  (create-table
   :user_fav_books
   [:user_id :integer]
   [:book_id :integer]
   [:rank :smallint]))

(defn drop-user_fav-books
  "deletes relation table user-fav-books"
  []
  (drop-table :user_fav_books))


(comment usage illustration

  (create-profiles)
  (drop-profiles)
  (create-books)
  (drop-books)
  (create-user-fav-books)
  (drop-user_fav-books))


;; --- information retrieval ---


; --- profiles ( internals ) ---

(declare user_fav_books)

(sql/defentity profiles
  (sql/has-many user_fav_books {:fk :user_id}))

(sql/defentity books
  (sql/has-many user_fav_books))

(sql/defentity user_fav_books
  (sql/pk :book_id)
  (sql/has-one profiles {:fk :user_id})
  (sql/has-one books {:fk :id}))



(def profile-cache (atom {}))
(declare write-user-fav-books)

(defn- flush-profile-cache
  "flushes the profile cache. This function is
   frequently invoked by a timer."
  [h]
  (doseq [[id fields] h]
    (let [fav-books (map json2clj-hash (:fav_books fields))
          fields (dissoc fields :fav_books)]
      (write-user-fav-books id fav-books) ; no modification date check here
      (insert-or-update-when-not-modified profiles id fields))))


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


(defn find-profile-old
  "finds profile with given keys in db.
   for request to update user profile data
   use get-profile instead in order to
   take into account the cache."
  [& {:as args}]
  (sql/select profiles (sql/where args)))


(defn find-profile
  "finds profile with given keys in db.
   for request to update user profile data
   use get-profile instead in order to
   take into account the cache."
  [& {:as args}]
  (let [_profiles (sql/select profiles (sql/where args))]
    (map (fn [profile]
           (let [fav-book-list
                 (sql/select user_fav_books
                             (sql/fields :books.author :books.title :rank)
                             (sql/with books)
                             (sql/where {:user_id (:id profile)}))]
             (assoc profile :user_fav_books fav-book-list)))
         _profiles)))


(comment usage illustration

         (find-profile2 :id 88)
         (find-profile2 :id 3))


(comment sql usage illustration

  (def a (sqlreq "SELECT * FROM `profiles` WHERE id=88;"))
  (def a (sqlreq
          "SELECT p.id,b.*
         FROM profiles p,user_fav_books as f, books as b
         WHERE p.id=f.user_id AND f.book_id=b.id;"))

  (def a (sqlreq
          "SELECT f.rank,b.author, b.title
         FROM profiles as p, user_fav_books as f, books as b
         WHERE p.id=f.user_id AND f.book_id=b.id AND p.id=88;"))

  (sql/sql-only
   (sql/select user_fav_books
               (sql/fields :books.author :books.title :rank)
               (sql/with books)
               (sql/where {:user_id 88}))))


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

  (def a (get-profile 88))
  (delete-profile :id 4)
  (swap! profile-cache flush-profile-cache)

  (start-profile-flush-cache-timer 60000)
  (stop-profile-flush-cache-timer)

  (flush-profile-cache @profile-cache)
  )


; --- user books ---



(defn add-book
  "add book which match given keys with fields"
  [what]
  (sql/insert books
              (sql/values what)))

(defn delete-book
  "delete books which match given keys with fields"
  [& {:as args}]
  (sql/delete books (sql/where args)))

(defn- update-user-fav-book-entry
  "helper function: updates a user-book relation
   table entry."
  [user-id book-id rank]
  (let [fav-entry {:user_id user-id :rank rank}
        fav-db-entry (first (sql/select user_fav_books (sql/where fav-entry)))]
    (if (not fav-db-entry)
      (do
        (println "new fav entry: " (assoc fav-entry :rank rank))
        (sql/insert user_fav_books (sql/values (assoc fav-entry :book_id book-id))))
      (sql/update user_fav_books
              (sql/set-fields {:book_id book-id})
              (sql/where fav-entry)))))

(defn write-user-fav-books
  "insert the favorite book list for the user with the
   given id into the table fav_book if the book is not
   already listed there and updates the relation table
   user_fav_books."
  [user-id fav-book-list]
  (doseq [book fav-book-list]
    (let [book-entry (select-keys book [:author :title])
          rank (:rank book)
          book-db-entry (first (sql/select
                                books
                                (sql/where (select-keys book [:author :title]))))]
      (if (not book-db-entry)
        (let [book-db-entry (sql/insert books (sql/values book-entry))
              book-id (:GENERATED_KEY book-db-entry)]
          (println "insert new book id: " book-id " -> " book-entry)
          (update-user-fav-book-entry user-id book-id rank))
        (update-user-fav-book-entry user-id (:id book-db-entry) rank)))))


(comment


  (write-user-fav-books
   88
   [{:author "Robert Musil" :title "Der Mann ohne Eigenschaften" :rank 9}
    {:author "Marcel Proust" :title "Verlorene Zeit" :rank 2}])

  )

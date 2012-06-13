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
        [project-alpha-server.app.zip-de]
        [ring.middleware.session.store :only [SessionStore]]
        [project-alpha-server.lib.crypto :only
         [get-secret-key get-encrypt-pass-and-salt]]
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
   [:user_country_code "varchar(3)"]
   [:user_zip "varchar(10)"]
   [:user_lat :double]
   [:user_lon :double]
   [:modified "timestamp"]
   ))


(defn drop-profiles
  []
  "Delete users table"
  (drop-table :profiles))

;; --- favorite books ---

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


;; --- favorite movies ---
;; field names are the same as favorite books for
;; common treatment

(defn create-movies
  "create favourite movie list"
  []
  (create-table
   :movies
   [:id :integer "PRIMARY KEY" "AUTO_INCREMENT"]
   [:author "varchar(255)"]
   [:title "varchar(255)"]
   [:isbn "varchar(13)"]))

(defn drop-movies
  "delete fav movies table"
  []
  (drop-table :movies))

(defn create-user-fav-movies
  "creates relation table user-fav-movies"
  []
  (create-table
   :user_fav_movies
   [:user_id :integer]
   [:book_id :integer]
   [:rank :smallint]))

(defn drop-user_fav-movies
  "deletes relation table user-fav-movies"
  []
  (drop-table :user_fav_movies))


;; --- favorite users ---

(defn create-user-fav-movies
  "creates relation table for favorite users"
  []
  (create-table
   :user_fav_users
   [:user_id :integer]
   [:match_id :integer]))

(defn drop-user-fav-movies
  "deletes relation table for favorite users"
  []
  (drop-table :user_fav_users))


(comment usage illustration

  (create-profiles)
  (drop-profiles)
  (create-books)
  (drop-books)
  (create-user-fav-books)
  (drop-user_fav-books)

  (create-movies)
  (drop-movies)
  (create-user-fav-movies)
  (drop-user_fav-movies)

  (create-user-fav-movies)
  (drop-user-fav-movies)
  )


;; --- information retrieval ---


; --- profiles ( internals ) ---

(declare user_fav_books)
(declare user_fav_movies)
(declare user_fav_users)


(sql/defentity profiles
  (sql/has-many user_fav_books {:fk :user_id})
  (sql/has-many user_fav_movies {:fk :user_id})
  (sql/has-many user_fav_users {:fk :user_id}))


(sql/defentity books
  (sql/has-many user_fav_books))

(sql/defentity user_fav_books
  (sql/pk :book_id)
  (sql/has-one profiles {:fk :user_id})
  (sql/has-one books {:fk :id}))


(sql/defentity movies
  (sql/has-many user_fav_movies))

(sql/defentity user_fav_movies
  (sql/pk :book_id)
  (sql/has-one profiles {:fk :user_id})
  (sql/has-one movies {:fk :id}))


(sql/defentity user_fav_users
  (sql/has-many profiles {:fk :id}))

(sql/defentity user_fav_movies
  (sql/pk :book_id)
  (sql/has-one profiles {:fk :user_id})
  (sql/has-one movies {:fk :id}))

(def profile-cache (atom {}))
(declare write-user-fav-books write-user-fav-movies check-profile-integrity)


(defn- flush-profile-cache-id
  "flushes the profile cache. This function is
   frequently invoked by a timer."
  [id h]
  (if-let [fields (h id)]
    (let [fav-books (map json2clj-hash (:fav_books fields))
          fav-movies (map json2clj-hash (:fav_movies fields))
          fields (dissoc fields :fav_books :fav_movies)
          zip (:user_zip fields)
          fields (if zip (merge fields (get-location-for-zip zip)) fields)]
      (write-user-fav-books id fav-books) ; no modification date check here
      (write-user-fav-movies id fav-movies) ; no modification date check here
      (insert-or-update-when-not-modified profiles id fields)
      (dissoc h id))
    h))


(defn- flush-profile-cache
  "flushes the profile cache. This function is
   frequently invoked by a timer."
  [h]
  (doseq [[id fields] h]
    (flush-profile-cache-id id h)
    (check-profile-integrity id)))


(defn start-profile-flush-cache-timer
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


(defn stop-profile-flush-cache-timer
  []
  "stops flushing the profile cache"
  (. profile-flush-timer (cancel)))





; --- profiles public API ---


(defn update-profile
  "queues new profile data for db storage"
  [id fields]
  (swap! profile-cache
         #(let [merged-fields (if % (merge (% id) fields) fields)
                mf-and-modified (assoc merged-fields
                                  :modified (java.util.Date.))]
            (assoc % id mf-and-modified))))


(defn flush-profile
  "writes profile data for a given id to the database. This is
   e.g. required before accessing client functions which retrieve
   this profile data (search)."
  [id]
  (swap! profile-cache #(when % (flush-profile-cache-id id %)))
  (println (str "flushed profile for user id " id))
  (check-profile-integrity id))


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
                             (sql/where {:user_id (:id profile)}))
                 fav-movie-list
                 (sql/select user_fav_movies
                             (sql/fields :movies.author :movies.title :rank)
                             (sql/with movies)
                             (sql/where {:user_id (:id profile)}))
                 full-prof (assoc profile
                             :user_fav_books fav-book-list
                             :user_fav_movies fav-movie-list)]
             (apply dissoc full-prof (map #(keyword (str "question_" %))
                                          (range (inc setup/nr-questions) (count full-prof))))))
         _profiles)))


(defn check-profile-integrity
  "checkes profile for missing and required data
   such as sex, interest sex, age, location,
   and questionaire data. when all data is given
   set user level to 1.
   function returns vector of stringed keys which
   are missed."
  [id]
  (println (str "-> check-profile-integrity for " id))
  (let [[prf] (find-profile :id id)
        [usr] (find-user-by-id id)
        chk-field-set #{:user_sex :user_interest_sex
                        :user_age :user_lon :user_lat}
        chk-field-set (reduce conj chk-field-set
                              (map #(keyword (str "question_" %)) (range 1 (inc setup/nr-questions))))
        prf (select-keys prf chk-field-set)
        missing-entries (filter #(not (val %)) prf)
        kw2str (fn [kw] (apply str (rest (str kw))))]
    (when (and (empty? missing-entries) (= 0 (usr :level)))
      (update-user {:level 1} {:id id}))
    (map #(kw2str (key %)) missing-entries)))



(comment usage illustration

         (find-profile :id 88)
         (find-profile :id 3)
         (def a (find-profile :id 6))

         (check-profile-integrity 6)
         (check-profile-integrity 5000)
         (check-profile-integrity 5059)
         (check-profile-integrity 5060)
         (find-profile :id 5060)
         )


(comment some sql usage illustration

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

(defn add-fav-user
  "add favorite user pair (add-fav-user :user_id x :match_id y)"
  [& {:as args}]
  (sql/insert user_fav_users
              (sql/values args)))

(defn delete-fav-user
  "delete favorite user (delete-fav-user :user_id x :match_id y)"
  [& {:as args}]
  (sql/delete user_fav_users (sql/where args)))

(defn get-all-fav-users-of
  "returns all favorite users of user with given id"
  [user-id]
  (map :match_id
       (sql/select user_fav_users
                   (sql/fields :match_id)
                   (sql/where {:user_id user-id}))))


(comment
  (add-fav-user :user_id 6 :match_id 562)
  (add-fav-user :user_id 6 :match_id 500)

  (delete-fav-user :user_id 6 :match_id 562)
  (delete-fav-user :user_id 6 :match_id 500)

  (def a (get-all-fav-users-of 6))

  )


;; --- user books ---

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


;; --- user movies ---

;; these are the same functions as for books
;; on the server we keep them separate

(defn add-movie
  "add movie which match given keys with fields"
  [what]
  (sql/insert movies
              (sql/values what)))

(defn delete-movie
  "delete movies which match given keys with fields"
  [& {:as args}]
  (sql/delete movies (sql/where args)))

(defn- update-user-fav-movie-entry
  "helper function: updates a user-movie relation
   table entry."
  [user-id movie-id rank]
  (let [fav-entry {:user_id user-id :rank rank}
        fav-db-entry (first (sql/select user_fav_movies (sql/where fav-entry)))]
    (if (not fav-db-entry)
      (do
        (println "new fav entry: " (assoc fav-entry :rank rank))
        (sql/insert user_fav_movies (sql/values (assoc fav-entry :book_id movie-id))))
      (sql/update user_fav_movies
              (sql/set-fields {:book_id movie-id})
              (sql/where fav-entry)))))

(defn write-user-fav-movies
  "insert the favorite movie list for the user with the
   given id into the table fav_movie if the movie is not
   already listed there and updates the relation table
   user_fav_movies."
  [user-id fav-movie-list]
  (doseq [movie fav-movie-list]
    (let [movie-entry (select-keys movie [:author :title])
          rank (:rank movie)
          movie-db-entry (first (sql/select
                                movies
                                (sql/where (select-keys movie [:author :title]))))]
      (if (not movie-db-entry)
        (let [movie-db-entry (sql/insert movies (sql/values movie-entry))
              movie-id (:GENERATED_KEY movie-db-entry)]
          (println "insert new movie id: " movie-id " -> " movie-entry)
          (update-user-fav-movie-entry user-id movie-id rank))
        (update-user-fav-movie-entry user-id (:id movie-db-entry) rank)))))


(comment


  (write-user-fav-books
   88
   [{:author "Robert Musil" :title "Der Mann ohne Eigenschaften" :rank 9}
    {:author "Marcel Proust" :title "Verlorene Zeit" :rank 2}])

  )

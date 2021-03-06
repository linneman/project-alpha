;;; project-alpha (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the GNU General Public License
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
            [local-settings :as setup]
            [clojure.string :as string])
  (:use [project-alpha-server.lib.model]
        [project-alpha-server.app.zip-de]
        [ring.middleware.session.store :only [SessionStore]]
        [project-alpha-server.lib.crypto :only
         [get-secret-key get-encrypt-pass-and-salt]]
        [clojure.data.json :only [json-str write-json read-json]]
        [project-alpha-server.lib.utils]
        [ring.util.response :only [response]])
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
   [:lang "varchar(3)" "DEFAULT 'de'"]
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
   [:max_dist :integer "DEFAULT 100"]
   [:created_before_max_days :integer "DEFAULT 30"]
   [:max_match_variance :integer "DEFAULT 50"]
   [:max_hits_vicinity :integer "DEFAULT 245"]
   [:max_hits_matching :integer "DEFAULT 245"]
   [:max_hits_recently_created :integer "DEFAULT 10"]
   [:mail_new_messages :boolean "DEFAULT TRUE"]
   [:mail_new_matches :boolean "DEFAULT TRUE"]
   [:modified "timestamp" "NOT NULL" "DEFAULT CURRENT_TIMESTAMP"]
   [:last_seek "timestamp" "NOT NULL" "DEFAULT '2013-01-01 8:00'"] ;; we need a valid ts here
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

(defn create-user-fav-users
  "creates relation table for favorite users"
  []
  (create-table
   :user_fav_users
   [:user_id :integer]
   [:match_id :integer]))

(defn drop-user-fav-users
  "deletes relation table for favorite users"
  []
  (drop-table :user_fav_users))

;; --- banned users ---

(defn create-user-banned-users
  "creates relation table for banned users"
  []
  (create-table
   :user_banned_users
   [:user_id :integer]
   [:match_id :integer]))

(defn drop-user-banned-users
  "deletes relation table for banned users"
  []
  (drop-table :user_banned_users))


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

  (create-user-fav-users)
  (drop-user-fav-users)
  (create-user-banned-users)
  (drop-user-banned-users)
  )

(defn create-messages
  "creates messages table"
  []
  (create-table
   :messages
   [:msg_id :integer "PRIMARY KEY" "AUTO_INCREMENT"]
   [:reference_msg_id :integer]
   [:from_user_id :integer]
   [:to_user_id :integer]
   [:creation_date "timestamp"]
   [:text "text"]))

(defn drop-messages
  "deletes messages table"
  []
  (drop-table :messages))

(defn create-unread-messages
  "create table refering unread messages"
  []
  (create-table
   :unread_messages
   [:unread_id :integer "PRIMARY KEY" "AUTO_INCREMENT"]
   [:user_id :integer]
   [:msg_id :integer]))

(defn drop-unread-messages
  "deletes table for unread messages"
  []
  (drop-table :unread_messages))


(comment usage illustration

  (create-messages)
  (drop-messages)
  (create-unread-messages)
  (drop-unread-messages)
  )


(defn create-email-notification-table
  "creates table which holds information whether
   we have already sent out an email notfication
   about new matches respectively new profiles."
  []
  (create-table
   :email_notification
   [:user_id :integer]
   [:notified_new_matches :boolean "DEFAULT FALSE"]
   [:notified_new_mail :boolean "DEFAULT FALSE"]))

(defn drop-email-notification-table
  "deletes table for email_notification"
  []
  (drop-table :email_notification))



;; --- information retrieval ---


; --- profiles ( internals ) ---

(declare user_fav_books)
(declare user_fav_movies)
(declare user_fav_users)
(declare user_banned_users)


(sql/defentity profiles
  (sql/has-many user_fav_books {:fk :user_id})
  (sql/has-many user_fav_movies {:fk :user_id})
  (sql/has-many user_fav_users {:fk :user_id}))

;; db concatenation of user and profile table
(sql/defentity user-profiles
  (sql/pk :id)
  (sql/table :users)
  (sql/has-one profiles {:fk :id}))

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

(sql/defentity user_banned_users
  (sql/has-many profiles {:fk :id}))

(sql/defentity user_fav_movies
  (sql/pk :book_id)
  (sql/has-one profiles {:fk :user_id})
  (sql/has-one movies {:fk :id}))


(def profile-cache
  (agent {}
         :error-mode :continue
         :error-handler
         (fn err-handler-fn [ag ex]
           (err-println "Exception" ex "occured while writing profile cache data" @ag))))

(declare write-user-fav-books write-user-fav-movies check-profile check-profile-integrity)


(defn- flush-profile-cache-id
  "flushes the profile cache. This function is
   frequently invoked by a timer."
  [id h]
  (if-let [fields (h id)]
    (let [fav-books (map json2clj-hash (:user_fav_books fields))
          fav-movies (map json2clj-hash (:user_fav_movies fields))
          fields (dissoc fields :user_fav_books :user_fav_movies)
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
    (check-profile id)))


(defn start-profile-flush-cache-timer
  "starts a timer which writes back the cached data
   for users profile after the specified timer in seconds."
  [period]
  (let [task (proxy [TimerTask] []
               (run []
                 (flush-profile-cache @profile-cache)
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
  (send-off profile-cache
         #(let [merged-fields (if % (merge (% id) fields) fields)
                mf-and-modified (if-not (empty? fields)
                                  (assoc merged-fields :modified (java.util.Date.))
                                  merged-fields)]
            (assoc % id mf-and-modified))))


(defn flush-profile
  "writes profile data for a given id to the database asynchronously.
   Function returns immediately"
  [id]
  (send-off profile-cache #(when % (flush-profile-cache-id id %)))
  (println (str "flushed profile for user id " id)))


(defn flush-all-profile-data
  "writes all cached data to db. currently exclusively required
   for testing and maintenance purposes"
  []
  (flush-profile-cache @profile-cache))


(defn check-profile
  "writes profile data for a given id to the database and waits until
   database operation has been completed (e.g. computation of coordinates
   for given user. This is e.g. required before accessing client functions
   which retrieve this profile data (search)."
  [id]
  (flush-profile id)
  (await-for 5000 profile-cache)
  (println (str "flush operation completed, now checked check profile for user id " id))
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
                             (sql/with books)
                             (sql/fields :books.author :books.title :rank)
                             (sql/where {:user_id (:id profile)}))
                 fav-movie-list
                 (sql/select user_fav_movies
                             (sql/with movies)
                             (sql/fields :movies.author :movies.title :rank)
                             (sql/where {:user_id (:id profile)}))
                 full-prof (assoc profile
                             :user_fav_books fav-book-list
                             :user_fav_movies fav-movie-list)]
             (apply dissoc full-prof (map #(keyword (str "question_" %))
                                          (range (inc setup/nr-questions) (count full-prof))))))
         _profiles)))


(defn find-user-profile
  "retrieves the joint tables user and profile"
  [id]
  (sql/select user-profiles
              (sql/with profiles)
              (sql/where (= :users.id id))))


(declare get-profile)

(defn- check-profile-integrity
  "checkes profile for missing and required data
   such as sex, interest sex, age, location,
   and questionaire data. when all data is given
   set user level to 1.
   function returns vector of stringed keys which
   are missed."
  [id]
  (println (str "-> check-profile-integrity for " id))
  (let [prf (get-profile id)
        [usr] (find-user-by-id id)
        chk-field-set #{:user_sex :user_interest_sex
                        :user_age :user_lon :user_lat}
        chk-field-set (reduce conj chk-field-set
                              (map #(keyword (str "question_" %)) (range 1 (inc setup/nr-questions))))
        prf (select-keys prf chk-field-set)
        missing-entries (filter #(not (val %)) prf)
        kw2str (fn [kw] (apply str (rest (str kw))))]
    (when (and (empty? missing-entries) (= 0 (usr :level)))
      (update-user {:level 1 :created_at (java.util.Date.)} {:id id}))
    (map #(kw2str (key %)) missing-entries)))


(defn update-profile-last-seek-to-now
  "in order to notify about new profiles
   this method allows to rember the timestamp
   of the last seek operation."
  [id]
  (sql/update profiles
              (sql/set-fields {:last_seek (java.util.Date.)})
              (sql/where {:id id})))


(defn update-profile-lang
  "update users language in table profiles for given user id"
  [id lang]
  (sql/update profiles
              (sql/set-fields {:lang lang})
              (sql/where {:id id})))


(defn get-profile-lang
  "get users last used language tag for given user id"
  [id]
  (let [[{lang :lang}]
        (sql/select profiles
                    (sql/fields :lang)
                    (sql/where {:id id}))]
    lang))


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
  "retrieves profile data from cache and database
   for given id."
  [id]
  (let [c @profile-cache
        u (if c (c id) {})
        d (first (find-profile :id id))]
    (merge d u)))


(defn delete-profile
  "deletes profile data for a given key"
  [& {:as args}]
  (sql/delete profiles (sql/where args)))


(defn delete-all-user-data
  "deletes everything expect user name and
   sent messages of given user id
   @todo: clean up favorite books, movies table."
  [id]
  (delete-profile :id id)
  (update-user {:level 0 :confirmed 0 :password "" :salt ""} {:id id})
  (response (forward-url "/clear-session")))


(comment
  usage illustration

  (update-profile 2 {:text "ABC"})
  (update-profile 3 {:text "Markus Linnemann"})
  (update-profile 3 {:text "Vorderster Linnemann"})
  (update-profile 3 {:text "Otto Linnemann"})
  (update-profile 4 {:text "Konrad Linnemann"})

  (def a (get-profile 88))
  (delete-profile :id 4)
  (send-off profile-cache flush-profile-cache)

  (start-profile-flush-cache-timer 60000)
  (stop-profile-flush-cache-timer)

  (flush-profile-cache @profile-cache)
  (delete-all-user-data 963)
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


(defn add-banned-user
  "add banned user pair (add-banned-user :user_id x :match_id y)"
  [& {:as args}]
  (sql/insert user_banned_users
              (sql/values args)))

(defn delete-banned-user
  "delete banned user (delete-banned-user :user_id x :match_id y)"
  [& {:as args}]
  (sql/delete user_banned_users (sql/where args)))

(defn get-all-banned-users-of
  "returns all banned users of user with given id"
  [user-id]
  (map :match_id
       (sql/select user_banned_users
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
  (let [fav-book-list (map #(assoc % :title (string/trim (:title %))
                                   :author (string/trim (:author %))) fav-book-list)]
    (doseq [book fav-book-list]
      (when-not (empty? (:title book))
        (let [book-entry (select-keys book [:author :title])
              rank (:rank book)
              book-db-entry (first (sql/select
                                    books
                                    (sql/where (select-keys book [:author :title]))))]
          (if (not book-db-entry)
            (let [book-db-entry (sql/insert books (sql/values book-entry))
                  book-id (or (:GENERATED_KEY book-db-entry) (:generated_key book-db-entry))]
              (println "insert new book id: " book-id " -> " book-entry)
              (update-user-fav-book-entry user-id book-id rank))
            (update-user-fav-book-entry user-id (:id book-db-entry) rank)))))))


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
  (let [fav-movie-list (map #(assoc % :title (string/trim (:title %))
                                    :author (string/trim (:author %))) fav-movie-list)]
    (doseq [movie fav-movie-list]
      (when-not (empty? (:title movie))
        (let [movie-entry (select-keys movie [:author :title])
              rank (:rank movie)
              movie-db-entry (first (sql/select
                                     movies
                                     (sql/where (select-keys movie [:author :title]))))]
          (if (not movie-db-entry)
            (let [movie-db-entry (sql/insert movies (sql/values movie-entry))
                  movie-id (or (:GENERATED_KEY movie-db-entry) (:generated_key movie-db-entry))]
              (println "insert new movie id: " movie-id " -> " movie-entry)
              (update-user-fav-movie-entry user-id movie-id rank))
            (update-user-fav-movie-entry user-id (:id movie-db-entry) rank)))))))


(comment


  (write-user-fav-books
   88
   [{:author "Robert Musil" :title "Der Mann ohne Eigenschaften" :rank 9}
    {:author "Marcel Proust" :title "Verlorene Zeit" :rank 2}])

  )


;; --- messages ---

(declare unread_messages)

(sql/defentity messages
  (sql/has-one unread_messages))

(sql/defentity unread_messages
  (sql/pk :msg_id)
  (sql/has-one messages {:fk :msg_id}))

(sql/defentity users-profiles-with-unread-messages
  (sql/pk :id)
  (sql/table :users)
  (sql/has-one profiles {:fk :id})
  (sql/has-many unread_messages {:fk :user_id}))


(defn add-msg
  "add message
   (add-msg :reference_msg_id x :from_user_id y :to_user_id z :text text)"
  [& {:as args}]
  (sql/insert messages (sql/values args)))

(defn delete-msg
  "delete message (delete-msg :message_id x)"
  [& {:as args}]
  (sql/delete messages (sql/where args)))


(defn add-unread-msg
  [& {:as args}]
  (sql/insert unread_messages (sql/values args)))

(defn delete-unread-msg
  [& {:as args}]
  (sql/delete unread_messages (sql/where args)))


;; usage illustration
; (add-msg :reference_msg_id 0 :from_user_id 6 :to_user_id 7 :text "Hallo Welt!")
; (delete-msg :msg_id 1)

(comment
  (add-msg :reference_msg_id 0 :from_user_id 6 :to_user_id 5061 :text "Hallo Sabinchen")
  (add-msg :reference_msg_id 3 :from_user_id 5061 :to_user_id 6 :text "Hallo Otto")
  (add-msg :reference_msg_id 4 :from_user_id 6 :to_user_id 5061 :text "Sabinchen, wie geht's?")
  (add-msg :reference_msg_id 5 :from_user_id 5061 :to_user_id 6 :text "Gut Otto, Gruss, Sabinchen")


  (add-msg :reference_msg_id 0 :from_user_id 6 :to_user_id 360 :text "Hallo Lisa")
  (add-msg :reference_msg_id 6 :from_user_id 5061 :to_user_id 6 :text "Ach Otto, meld' Dich doch mal bei Sabinchen")
  (add-msg :reference_msg_id 7 :from_user_id 360 :to_user_id 6 :text "Gruesse von Lisa")
  (add-msg :reference_msg_id 9 :from_user_id 6 :to_user_id 366 :text "Gruesse von Otto")

  (add-msg :from_user_id 963 :to_user_id 6 :text "Gruesse von Lisa")

  (add-unread-msg :msg_id 3)
  (delete-unread-msg :msg_id 3)
  )


(defn- delete-from-unread-msg
  "removes messages from unread table when
   message correspondence stream is recieived
   refer also to function get-correspondence"
  [from_user_id to_user_id]
  (let [msg-to-delete
        (sql/select unread_messages
                    (sql/fields :unread_id)
                    (sql/with messages)
                    (sql/where
                     (and
                      {:user_id from_user_id}
                      {:messages.from_user_id to_user_id}
                      {:messages.to_user_id from_user_id})))
        msg-to-delete (map #(:unread_id %) msg-to-delete)]
    ;(println "(delete-from-unread-msg " from_user_id to_user_id ")")
    (when (not (empty? msg-to-delete))
      (sql/delete unread_messages
                  (sql/where (in :unread_id msg-to-delete))))))


(defn- get-received-messages
  "retrieves all messages addressed to the user
   with the id 'to_user_id'."
  [user-id]
  (sql/select messages
                    (sql/where {:to_user_id user-id})
                    (sql/order :creation_date :DESC)))


(defn- filter-last-received-messages
  "filters the latest messages from the output of
   (get-received-messages)."
  [all-recv-msgs]
  (let [all-senders (set (map :from_user_id all-recv-msgs))]
    (map
     (fn [from]
       (last (sort-by :creation_date (filter #(= from (:from_user_id %)) all-recv-msgs))))
     all-senders)))


(defn- filter-read-messages
  "filters messages that have been read already"
  [msgs user-id]
  (let [unread-msg-ids
        (set (map :msg_id
                  (sql/select unread_messages
                              (sql/where {:user_id user-id}))))]
    (filter #(not (contains? unread-msg-ids (:msg_id %))) msgs)))


(defn- html-to-short-txt
  "Removes all html attributes in a given string and cuts this
   string to the given length. This is required for listing
   exclusively the first sentence of a couple of messages."
  [html len]
  (let [txt (-> html
                (.replaceAll "<div>" " ")
                (.replaceAll "<[^>]+>" ""))]
    (if (> (.length txt) len)
      (str (.substring txt 0 (- len 4)) " ...")
      txt)))


(defn- sql-resp-transform-html-to-short-txt
  "Shortens message text for table view by applying the function
   html-to-short-txt to strings for given key in a sql response."
  [sql-res key]
  (map
   #(assoc-in % [key]
              (html-to-short-txt (str (% key)) 80))
   sql-res))


(defn- transform-sql-resp
  "transform sql response to application specific json
   object"
  ([sql-res] (transform-sql-resp sql-res :from_user_id))
  ([sql-res key]
     (let [hash-by-from_id #(sql-resp-2-hash-by-id % key)
           ger-creation-date #(sql-resp-transform-to-german-date % :creation_date)
           html-to-short-txt #(sql-resp-transform-html-to-short-txt % :text)]
       (-> sql-res
           ger-creation-date
           html-to-short-txt
           hash-by-from_id))))


(defn get-read-messages
  "retrieves the lastest messages addressed to the user
   with given id."
  [user-id]
  (let [all-recv-msgs (get-received-messages user-id)
        last-recv-msgs (filter-last-received-messages all-recv-msgs)
        last-recv-msgs (filter-read-messages last-recv-msgs user-id)
        res (map #(let [[{from_user_name :name}] (find-user-by-id (:from_user_id %))
                        answered (not
                                  (empty?
                                   (sql/select messages
                                               (sql/where {:reference_msg_id (:msg_id %)}))))]
                    (into % {:from_user_name from_user_name :answered answered})) ;; attach user name and answered flag
                 last-recv-msgs)]
    (transform-sql-resp res)))


(declare set-new-mail-notifier-for)

(defn get-unread-messages
  "retreives all messages that have not been read yet."
  [user-id]
  (let [res (sql/select unread_messages
                        (sql/with messages)
                        (sql/fields :msg_id :messages.reference_msg_id
                                    :messages.from_user_id :messages.to_user_id
                                    :messages.creation_date :messages.text)
                        (sql/where
                         {:user_id user-id :msg_id [> 0]}))
        res (map #(let [[{from_user_name :name}] (find-user-by-id (:from_user_id %))]
                    (into % {:from_user_name from_user_name})) ;; attach user name
                 res)]
    (when-not (empty? res)
      (set-new-mail-notifier-for user-id false) ; allow new notification to be sent
      (transform-sql-resp res))))


(defn get-unanswered-messages
  "retrieves all messages the user has sent but they have been
   not answered yet."
  [user-id]
  (let [all-recv-msgs (get-received-messages user-id)
        all-sender-ids (reduce #(conj % (:from_user_id %2)) #{} all-recv-msgs)
        all-sent-msgs
        (sql/select messages
                    (sql/where {:from_user_id user-id}))
        res (filter identity
                    (map (fn [{to-user-id :to_user_id :as user}]
                           (when-not (all-sender-ids to-user-id) user))
                         all-sent-msgs))
        res (map #(let [[{to_user_name :name}] (find-user-by-id (:to_user_id %))]
                    (into % {:to_user_name to_user_name})) ;; attach user name
                 res)]
    (transform-sql-resp res :to_user_id)))


(comment usage-illustration

         (get-read-messages 6)
         (get-unread-messages 6)
         (get-unread-messages 1002)
         (get-unanswered-messages 6)
         )


(defn get-correspondence
  "retrieves the complete communication between two users
   the function returns an array of two entries. The first
   element provides a hash table with the users name for
   the given id's. The second element provides all messages
   exchanged between both users in descending order
   (most recent message comes first)."
  [from_user_id to_user_id]
  (let [[{from_user_name :name}] (find-user-by-id from_user_id)
        [{to_user_name :name}] (find-user-by-id to_user_id)]
    (when (and from_user_name to_user_name)
      (let [db_res (sql/select messages
                               (sql/where
                                (or
                                 (and {:from_user_id from_user_id} {:to_user_id to_user_id})
                                 (and {:from_user_id to_user_id} {:to_user_id from_user_id})))
                               (sql/order :creation_date :DESC))]
        (delete-from-unread-msg from_user_id to_user_id)
        [{:from-id from_user_id :from-name from_user_name
          :to-id to_user_id :to-name to_user_name}
         (sql-resp-transform-to-german-date db_res :creation_date)]))))


(comment usage illustration
         (get-correspondence 6 5061)
         (get-correspondence 5061 6)

         (json-str (get-correspondence 6 5061))
         (json-str (get-correspondence 5061 6))
         )


(defn new-message
  "creates a new message for key value args
   :sender-id :recv-id and :msg-txt"
  [& {:keys [sender-id recv-id msg-txt] :as args}]
  (println args)
  (println "->from:" sender-id ", ->to:" recv-id)
  (let [corr (get-correspondence sender-id recv-id)
        ref-id (if corr
                 (:msg_id (first (second corr)))
                 0)]
    (let [sql-resp (add-msg :reference_msg_id ref-id :from_user_id sender-id :to_user_id
                            recv-id :text msg-txt)
          id (or (:GENERATED_KEY sql-resp) (:generated_key sql-resp))] ; sql versions behave different!
      (when id
        (add-unread-msg :user_id recv-id :msg_id id))
      "OK")))

(comment
  (new-message :sender-id 6 :recv-id "561" :msg-txt "Hallo Welt")
  )



;; --- email notification ---

(sql/defentity email_notification)


(defn set-new-matches-notifier-for
  "sets or clears db state indicating whether we
   have already sent out an email about new matches"
  [user-id state]
  (if (empty? (sql/select email_notification (sql/where {:user_id user-id})))
    (sql/insert email_notification
                (sql/values {:user_id user-id :notified_new_matches state}))
    (sql/update email_notification
                (sql/set-fields {:notified_new_matches state})
                (sql/where {:user_id user-id}))))

(defn is-user-notified-about-new-matches?
  "retrieves db state about new matches"
  [user-id]
  (let [[{:keys [notified_new_matches]}]
      (sql/select email_notification
                  (sql/fields :notified_new_matches)
                  (sql/where {:user_id user-id}))]
               notified_new_matches))

(defn set-new-mail-notifier-for
  "sets or clears db state indicating whether we
   have already sent out an email about new mail"
  [user-id state]
  (if (empty? (sql/select email_notification (sql/where {:user_id user-id})))
    (sql/insert email_notification
                (sql/values {:user_id user-id :notified_new_mail state}))
    (sql/update email_notification
                (sql/set-fields {:notified_new_mail state})
                (sql/where {:user_id user-id}))))

(defn is-user-notified-about-new-mail?
  "retrieves db state about new matches"
  [user-id]
  (let [[{:keys [notified_new_mail]}]
      (sql/select email_notification
                  (sql/fields :notified_new_mail)
                  (sql/where {:user_id user-id}))]
               notified_new_mail))

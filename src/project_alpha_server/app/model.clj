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


(def profile-cache
  (agent {}
         :error-mode :continue
         :error-handler
         (fn err-handler-fn [ag ex]
           (err-println "Exception" ex "occured while writing profile cache data" @ag))))

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
                 (send-off profile-cache flush-profile-cache)
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
                mf-and-modified (assoc merged-fields
                                  :modified (java.util.Date.))]
            (assoc % id mf-and-modified))))


(defn flush-profile
  "writes profile data for a given id to the database. This is
   e.g. required before accessing client functions which retrieve
   this profile data (search)."
  [id]
  (send-off profile-cache #(when % (flush-profile-cache-id id %)))
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
  (flush-profile id)
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
  (send-off profile-cache flush-profile-cache)

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


;; --- messages ---

(declare unread_messages)

(sql/defentity messages
  (sql/has-one unread_messages))

(sql/defentity unread_messages
  (sql/pk :msg_id)
  (sql/has-one messages {:fk :msg_id}))


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

(comment

  (defn- get-received-messages2
    "retrieves all messages addressed to the user
   with the id 'to_user_id'."
    [user-id]
    (sqlreq
     "SELECT messages.msg_id, messages.from_user_id, messages.creation_date, messages.text,
     ACOS(
       SIN(RADIANS(profiles.user_lat)) * SIN(RADIANS(50.11))
       + COS(RADIANS(profiles.user_lat)) * COS(RADIANS(50.11)) * COS(RADIANS(profiles.user_lon)
       - RADIANS(8.68))
       ) * 6380 AS distance,

     FROM messages
     LEFT JOIN users
     ON users.id = messages.from_user_id
     LEFT JOIN profiles
     ON profiles.id = messages.from_user_id
     WHERE messages.to_user_id = 6
     ORDER BY messages.creation_date DESC;")
    )

  (reduce
   #(str %1 "+" (format "POW(users.question_%d-profiles.question_%d,2)" %2 %2))
   ""
   (range 1 (inc setup/nr-questions)))

  )



(defn- filter-last-received-messages
  "filters the lastest messages from the output of
   (get-received-messages)."
  [all-recv-msgs]
  (let [all-senders (set (map :from_user_id all-recv-msgs))]
    (map
     (fn [from]
       (last (sort-by :creation_date (filter #(= from (:from_user_id %)) all-recv-msgs))))
     all-senders)))


(defn- get-unanswered-messages
  "retries all messages the user has sent but they have been
   not answered yet. Expects output form (get-received-messages)
   as second argument."
  [user-id all-recv-msgs]
  (let [all-sender-ids (reduce #(conj % (:from_user_id %2)) #{} all-recv-msgs)
        all-sent-msgs
        (sql/select messages
                    (sql/where {:from_user_id user-id}))]
    (filter identity
            (map (fn [{to-user-id :to_user_id :as user}]
                   (when-not (all-sender-ids to-user-id) user))
                 all-sent-msgs))))


(defn get-all-messages
  "retrieves the lastest messages addressed to the user
   with given id. Furthermore also message which have
   been send out but are not ansered yet are given back."
  [user-id]
  (let [all-recv-msgs (get-received-messages user-id)
        last-recv-msgs (filter-last-received-messages all-recv-msgs)
        unanswered-messages (get-unanswered-messages user-id all-recv-msgs)
        res (map #(let [[{from_user_name :name}] (find-user-by-id (:from_user_id %))]
                    (into % {:from_user_name from_user_name})) ;; attach user name
                 (concat last-recv-msgs unanswered-messages))]
    (map #(assoc % :creation_date (str (:creation_date %))) res)))


(defn get-unread-messages
  "retreives all messages that have not been read yet."
  [user-id]
  (let [res (sql/select unread_messages
                        (sql/with messages)
                        (sql/fields :msg_id :messages.reference_msg_id
                                    :messages.from_user_id :messages.to_user_id
                                    :messages.creation_date :messages.text)
                        (sql/where
                         {:user_id user-id}))]
    (map #(assoc % :creation_date (str (:creation_date %))) res)))



(comment usage-illustration

         (def all-recv-msgs (get-received-messages 6))
         (filter-last-received-messages all-recv-msgs)
         (get-unanswered-messages 6 all-recv-msgs)

         (get-all-messages 6 )
         (get-unread-messages 1002)
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
         (map #(assoc % :creation_date (str (:creation_date %))) db_res)]))))


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
    (when-let [{id :GENERATED_KEY}
               (add-msg :reference_msg_id ref-id :from_user_id sender-id :to_user_id
                                            recv-id :text msg-txt)]
      (add-unread-msg :user_id recv-id :msg_id id)
      "OK")))

(comment
  (new-message :sender-id 6 :recv-id "561" :msg-txt "Hallo Welt")
  )

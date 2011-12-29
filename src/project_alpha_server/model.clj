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

(ns project-alpha-server.model
  (:require [korma.db :as db]
            [korma.core :as sql]
            [clojure.java.jdbc :as jdbc]
            [ring.util.codec :as codec])
  (:use [ring.middleware.session.store :only [SessionStore]]
        [project-alpha-server.crypto :only
         [get-secret-key get-encrypt-pass-and-salt decrypt-pass]]))

;; The database connection
;; argument for sql requests
(def db-con
  (db/mysql {:db "project-alpha"
             :host "localhost"
             :port 8889
             :user "project-alpha"
             :password "test"}))

;; korma convenience layer
(db/defdb db db-con)


;;; --- utility functions ---

(defn sqlreq
  "sends an sql request to the database
   example:
    (sqlreq 'select count(*) from users')"
  [req]
  (jdbc/with-connection db-con
    (jdbc/with-query-results rs
      [req]
      (doall rs))))

(defn sqltact
  "trigger sql transaction"
  [transaction]
  (jdbc/with-connection db-con
    (jdbc/transaction (transaction)
     )))

(defn create-table
  "Create table with given spec"
  [table & spec]
  (sqltact #(apply jdbc/create-table (conj spec table))))

(defn drop-table
  "Drop the specified table"
  [table]
  (jdbc/with-connection db-con
    (jdbc/transaction
     (try
       (clojure.java.jdbc/drop-table table)
       (catch Exception _)))))



;; --- table creation and deletion ---


;; --- users ---

(defn create-users
  "Create user table"
  []
  (create-table
   :users
   [:id :integer "PRIMARY KEY" "AUTO_INCREMENT"]
   [:name "varchar(255)" "UNIQUE"]
   [:email "varchar(255)" "UNIQUE"]
   [:password "varchar(255)"]
   [:salt "varchar(255)"]
   [:confirmation_link "varchar(255)"]
   [:level :integer]
   [:confirmed :boolean]
   [:created_at "datetime"]
   ))


(defn drop-users
  []
  "Delete users table"
  (drop-table :users))

(comment usage illustration

  (create-users)
  (drop-users))


;; --- sessions ---

(defn create-sessions
  "Create session table"
  []
  (create-table
   :sessions
   [:id :integer "PRIMARY KEY" "AUTO_INCREMENT"]
   [:session_id "varchar(255)" "UNIQUE"]
   [:data "text"]
   [:created_at "datetime"]
   ))

(defn drop-sessions
  []
  "Delete users table"
  (drop-table :sessions))

; (create-sessions)
; (drop-sessions)


;; --- information retrieval ---


;; --- users ---

(sql/defentity users)

(defn add-user
  "add a new user"
  [& {:keys [name email password level confirmed]
                   :or {level 0 confirmed false}}]
  (let [{:keys [password salt]} (get-encrypt-pass-and-salt password)]
    (sql/insert users (sql/values
                       {:name name
                        :email email
                        :password password
                        :salt salt
                        :level level
                        :confirmed confirmed
                        :created_at (java.util.Date.)}))))

(defn find-user
  "finds user with given keys"
  [& {:as args}]
  (sql/select users (sql/where args)))

(defn update-user
  "update user which match given keys with fields"
  [fields where]
  (sql/update users
              (sql/set-fields fields)
              (sql/where where)))

(defn delete-user
  "deletes session data for a given key"
  [& {:as args}]
  (sql/delete users (sql/where args)))

(defn change-user-password
  "updates the user password"
  [newpassword where]
  (let [{:keys [password salt]} (get-encrypt-pass-and-salt newpassword)]
    (sql/update users
                (sql/set-fields  {:password password :salt salt})
                (sql/where where))))

(defn check-user-password
  "check the save user password agains the login"
  [pass-entered where]
  (let [users (sql/select users (sql/where where))]
    (if (empty? users)
      false
      (let [[user] users
            pass-decrypted (decrypt-pass (:password user) (:salt user))]
        (= pass-entered pass-decrypted)))))

(defn find-user-by-name [name]
  (find-user :name name))

(defn find-user-by-email [email]
  (find-user :email email))

(defn find-user-by-id [id]
  (find-user :id id))


(comment
  usage illustration

  (add-user :name "Otto" :email "linneman@gmx.de" :password "secret")
  (add-user :name "Konrad" :email "Konrad.Linnemann@google.de" :password "secret")
  (change-user-password "mynewpassword" {:name "Otto"})
  (check-user-password "mynewpassword" {:name "Otto"})
  (delete-user :name "Otto")

  (find-user :name "Otto")
  (find-user-by-name "Otto")
  (find-user-by-email "linneman@gmx.de")
  (find-user-by-id 2)

  (update-user {:email "Otto.Linnemann@google.de"} {:name "Otto"})
  (update-user {:email "Otto.Linnemann@google.de"} {:name "Doppelgänger"})

  (sql/select users (sql/where {:name "otto"})))



;; --- sessions ---

(sql/defentity sessions)

(defn add-session
  "add a new session"
  [& {:keys [session_id data]}]
  (sql/insert sessions (sql/values
                     {:session_id session_id :data data :created_at (java.util.Date.)})))

(defn find-session
  "finds session with given keys"
  [& {:as args}]
  (sql/select sessions (sql/where args)))

(defn update-session
  "update session which match given keys with fields"
  [fields where]
  (sql/update sessions
          (sql/set-fields fields)
          (sql/where where)))

(defn read-session-data
  "reads the session data for a given key"
  [key]
  (let [res (find-session :session_id key)]
    (if (empty? res) {} (read-string (:data (res 0))) )))

(defn delete-session-data
  "deletes session data for a given key"
  [key]
  (sql/delete sessions (sql/where {:session_id key})))

(defn write-session-data
  "write respectively updates session data for key"
  [key data]
  (let [dataenc (pr-str data)]
    (cond
     (empty? data) (delete-session-data key)
     (empty? (read-session-data key)) (add-session :session_id key :data dataenc)
     true (update-session {:data dataenc} {:session_id key}))
    dataenc))

(deftype DbSessionStore []
  SessionStore
  (read-session [_ key]
    (println "read-session: key->" key) (read-session-data key))
  (write-session [_ key data]
    (let [key (or key (codec/base64-encode (get-secret-key {})))]
      (println "write-session: key->" key "data->" data) (write-session-data key data)
      key))
  (delete-session [_ key]
    (println "delete-session: key->" key) (delete-session-data key)
    nil))

(defn db-session-store
  "creates the persistent session store (via database)"
  []
  (DbSessionStore.))

(comment
  usage illustration

  (write-session-data "123" "data1")
  (write-session-data "456" "data2")

  (read-session-data "123")
  (write-session-data "123" "data 42")
  (delete-session-data "123"))

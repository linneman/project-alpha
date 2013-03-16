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

(ns project-alpha-server.app.find-users
  (:require [korma.db :as db]
            [korma.core :as sql]
            [clojure.java.jdbc :as jdbc]
            [ring.util.codec :as codec]
            [project-alpha-server.local-settings :as setup]
            [clojure.string :as string])
  (:use [project-alpha-server.app.model]
        [project-alpha-server.lib.model]
        [project-alpha-server.app.zip-de]
        [clojure.data.json :only [json-str write-json read-json]]
        [project-alpha-server.lib.utils]
        [macros.macros])
  (:import [java.text DateFormat SimpleDateFormat]
           [java.util TimerTask Timer]))


(defn- generate-sql-match-calc-exp
  "generates the SQL expressiong to calculate the variance
   of the questionnaire answers between user and profile data.
   Since it should be easy to increase the number of questions
   we dynmically generate the appropriate SQL for incorporate
   the currently used questions according to 'setup/nr-questions'.

   The function generates an SQL expression string of the form:

   POW($question_1$ - prf.question_1, 2) +
   POW($question_2$ - prf.question_2, 2) +
   ...

   POW($question_<nr-questions>$ - prf.question_<nr-questions>, 2)"
  []
  (let [varexp (reduce
                #(str %1 "+" (format "POW($question_%d$-prf.question_%d,2)" %2 %2))
                ""
                (range 1 (inc setup/nr-questions)))
        varexp (. varexp (substring 1))]
    varexp))


(defn- generate-sql-match-as-exp
  "generates the SQL expressiong to calculate the variance
   of the questionnaire answers between user and profile data.
   Since it should be easy to increase the number of questions
   we dynmically generate the appropriate SQL for incorporate
   the currently used questions according to 'setup/nr-questions'.

   The function generates an SQL expression string of the form:

   POW($question_1$ - prf.question_1, 2) +
   POW($question_2$ - prf.question_2, 2) +
   ...

   POW($question_<nr-questions>$ - prf.question_<nr-questions>, 2)
        AS match_variance"
  []
  (str "\n" (generate-sql-match-calc-exp) " AS match_variance\n"))


(defn- generate-sql-match-where-exp
  "generates the SQL expressiong to filter a low variance
   of the questionnaire answers between user and profile data.
   Since it should be easy to increase the number of questions
   we dynmically generate the appropriate SQL for incorporate
   the currently used questions according to 'setup/nr-questions'.

   The function generates an SQL expression string of the form:

   WHERE POW($question_1$ - prf.question_1, 2) +
         POW($question_2$ - prf.question_2, 2) +
         ...

         POW($question_<nr-questions>$ - prf.question_<nr-questions>, 2)
         < $max-match-variance$"
  []
  (str "\nWHERE " (generate-sql-match-calc-exp) " < $max-match-variance$\n"))


(defn- find-users-in-vicinity
  "requests all zip-positions to the vicinity of a
   given position and distance. For each hit the
   locations zip code, the locations name and the
   distance to the given position is returned."
  [& {:keys [user_lon user_lat max-dist user_sex user_interest_sex limit user-id] :as args}]
  (let [req (str
              "SELECT usr.id, usr.name, usr.created_at,
               ACOS(
                    SIN(RADIANS(prf.user_lat)) * SIN(RADIANS($user_lat$))
                    + COS(RADIANS(prf.user_lat)) * COS(RADIANS($user_lat$)) * COS(RADIANS(prf.user_lon)
                    - RADIANS($user_lon$))
                    ) * 6380 AS distance,"
              (generate-sql-match-as-exp)
              "FROM profiles prf LEFT JOIN users usr ON prf.id = usr.id
               WHERE usr.id NOT IN(SELECT match_id FROM user_banned_users WHERE user_id=$user-id$)
               AND ACOS(
                    SIN(RADIANS(prf.user_lat)) * SIN(RADIANS($user_lat$))
                    + COS(RADIANS(prf.user_lat)) * COS(RADIANS($user_lat$)) * COS(RADIANS(prf.user_lon)
                    - RADIANS($user_lon$))
                    ) * 6380 < $max-dist$
               AND prf.user_sex = \"$user_sex$\"
               AND prf.user_interest_sex = \"$user_interest_sex$\"
               AND usr.level = 1
               ORDER BY distance
               LIMIT $limit$;")
        req (replace-dollar-template-by-keyvals req args)]
    (sqlreq req)))


(defn- find-matching-users
  "request all users with same sexual interest and
   best matching of the questionaire."
  [& {:keys [user_lon user_lat user_sex user_interest_sex
             max-match-variance limit user-id] :as args}]
  (let [req (str "SELECT usr.id, usr.name, usr.created_at,
                  ACOS(
                       SIN(RADIANS(prf.user_lat)) * SIN(RADIANS($user_lat$))
                       + COS(RADIANS(prf.user_lat)) * COS(RADIANS($user_lat$)) * COS(RADIANS(prf.user_lon)
                       - RADIANS($user_lon$))
                       ) * 6380 AS distance,"
                 (generate-sql-match-as-exp)
                 "FROM profiles prf LEFT JOIN users usr ON prf.id = usr.id"
                 (generate-sql-match-where-exp)
                 "AND usr.id NOT IN(SELECT match_id FROM user_banned_users WHERE user_id=$user-id$)"
                 "AND prf.user_sex = \"$user_sex$\"
                  AND prf.user_interest_sex = \"$user_interest_sex$\"
                  AND usr.level = 1
                  ORDER BY match_variance asc
                  LIMIT $limit$;")
        req (replace-dollar-template-by-keyvals req args)]
    (sqlreq req)))


(defn- find-recent-users
  "request all new user where with same sexual interest"
  [& {:keys [user_lon user_lat user_sex user_interest_sex
             question_1 question_2 question_3
             question_4 question_5 question_6
             question_7 question_8 question_9
             question_10 created-before-max-days limit user-id] :as args}]
  (let [req (str "SELECT usr.id, usr.name, usr.created_at,
                  ACOS(
                       SIN(RADIANS(prf.user_lat)) * SIN(RADIANS($user_lat$))
                       + COS(RADIANS(prf.user_lat)) * COS(RADIANS($user_lat$)) * COS(RADIANS(prf.user_lon)
                       - RADIANS($user_lon$))
                       ) * 6380 AS distance,"
                 (generate-sql-match-as-exp)
                 "FROM profiles prf LEFT JOIN users usr ON prf.id = usr.id
                  WHERE DATE_SUB(CURDATE(),INTERVAL $created-before-max-days$ DAY)
                                <= usr.created_at
                  AND usr.id NOT IN(SELECT match_id FROM user_banned_users WHERE user_id=$user-id$)
                  AND prf.user_sex = \"$user_sex$\"
                  AND prf.user_interest_sex = \"$user_interest_sex$\"
                  AND usr.level = 1
                  ORDER BY usr.created_at desc
                  LIMIT $limit$;")
        req (replace-dollar-template-by-keyvals req args)]
    (sqlreq req)))


(defn- find-fav-users
  "request all favorite users"
  [& {:keys [user_lon user_lat user_sex user_interest_sex
             created-before-max-days limit] :as args}]
  (let [req (str "SELECT usr.id, usr.name, usr.created_at,
                  ACOS(
                       SIN(RADIANS(prf.user_lat)) * SIN(RADIANS($user_lat$))
                       + COS(RADIANS(prf.user_lat)) * COS(RADIANS($user_lat$)) * COS(RADIANS(prf.user_lon)
                       - RADIANS($user_lon$))
                       ) * 6380 AS distance,"
                 (generate-sql-match-as-exp)
                 "FROM profiles prf JOIN users usr ON prf.id = usr.id
                  JOIN user_fav_users fav ON fav.match_id = prf.id
                  WHERE fav.user_id = $id$
                  AND usr.level = 1
                  ORDER BY usr.created_at desc
                  LIMIT $limit$;")
        req (replace-dollar-template-by-keyvals req args)]
    (sqlreq req)))


(defn- find-banned-users
  "request all banned users"
  [& {:keys [user_lon user_lat user_sex user_interest_sex
             created-before-max-days limit] :as args}]
  (let [req (str "SELECT usr.id, usr.name, usr.created_at,
                  ACOS(
                       SIN(RADIANS(prf.user_lat)) * SIN(RADIANS($user_lat$))
                       + COS(RADIANS(prf.user_lat)) * COS(RADIANS($user_lat$)) * COS(RADIANS(prf.user_lon)
                       - RADIANS($user_lon$))
                       ) * 6380 AS distance,"
                 (generate-sql-match-as-exp)
                 "FROM profiles prf JOIN users usr ON prf.id = usr.id
                  JOIN user_banned_users banned ON banned.match_id = prf.id
                  WHERE banned.user_id = $id$
                  AND usr.level = 1
                  ORDER BY usr.created_at desc
                  LIMIT $limit$;")
        req (replace-dollar-template-by-keyvals req args)]
    (sqlreq req)))


(defn- sql-resp-transform-variance
  "express match variance in percent"
  [sql-res]
  (let [max-var 16
        var2per (fn [var] (when var (Math/round (* 100 (/ var (* setup/nr-questions max-var))))))]
    (map
     #(assoc-in % [:match_variance] (var2per (% :match_variance)))
     sql-res)))


(defn- sql-resp-transform-dist
  "round distance to kilometers"
  [sql-res]
  (map
   #(assoc-in % [:distance] (when-let [d (% :distance)] (Math/round d)))
   sql-res))


(defn- transform-sql-resp
  "transform sql response to application specific json
   object"
  [sql-res]
  (let [hash-by-id #(sql-resp-2-hash-by-id % :id)
        ger-creation-date #(sql-resp-transform-to-german-date % :created_at)]
    (-> sql-res
        ger-creation-date
        sql-resp-transform-variance
        sql-resp-transform-dist
        hash-by-id)))


(defn- map-sex-interest
  "assigns the fields user_sex and user_interest_sex
   in the given hash kv for matching."
  [kv]
  (-> kv
      (assoc-in [:user_sex] (kv :user_interest_sex))
      (assoc-in [:user_interest_sex] (kv :user_sex))))


(defn find-all-matches
  "database retrieval for profile matches. Matching is done under several
   aspects where sexual orientation must always match:
   1. users who live in vicinity ('max-dist' kilometers away)
   2. users whose profiles fit best (questionaire variance less than 'max-match-variance')
   3. recently created accounts (after 'created_before-max-days', to allow for tracking new members)
   The results are limited to the values in the keys 'max-hits-vicinity',
   'max-hits-matching' and max-hits-recently-created' and merged afterword.
   The function returns all hits as hash table with the user-id as key and
   the user's name, match_variance, distance and creation date as values
   in JSON format. The client application (ClojureScript) allows to sort
   the data presented in a table format without a new request afterwards."
  [& {:keys [user-id max-dist max-match-variance created-before-max-days
             max-hits-vicinity max-hits-matching max-hits-recently-created]
      :or {max-dist 100
           max-match-variance 50
           created-before-max-days 30
           max-hits-vicinity 245
           max-hits-matching 245
           max-hits-recently-created 10} :as args}]
  (let [[{:keys [level]}] (find-user-by-id user-id)]
    (if (= level 1)
      (let [max-var 16
            per2var (fn [var] (* setup/nr-questions max-var (/ var 100.0)))
            max-match-variance (per2var max-match-variance)
            usr-prf (assoc (first (find-profile :id user-id)) :user-id user-id)
            trg-prf (map-sex-interest usr-prf)
            match-prf (mapcat #(vector (key %) (val %))
                              (merge trg-prf (hash-args max-dist max-match-variance created-before-max-days)))
            matches (map #(transform-sql-resp (apply %1 (conj match-prf %2 :limit)))
                         [find-users-in-vicinity find-matching-users find-recent-users]
                         [max-hits-vicinity max-hits-matching max-hits-recently-created])
            matches (reduce merge matches)
            matches (dissoc matches user-id) ; make sure not to integrate the user himself
            ]
        (do
          (update-profile-last-seek-to-now user-id)
          {:data matches}))
      {:error (check-profile user-id)})))


(defn find-all-favorites
  "database retrieval for favorite matches."
  [& {:keys [user-id limit] :or {limit 100} :as args}]
  (if-let [[{:keys [level]}] (find-user-by-id user-id)]
    (if (= level 1)
      (let [usr-prf (first (find-profile :id user-id))
            usr-prf (mapcat #(vector (key %) (val %))
                            (merge usr-prf (hash-args limit)))
            matches (transform-sql-resp (apply find-fav-users usr-prf))]
        {:data matches})
      {:error (check-profile user-id)})))


(defn find-all-banned
  "database retrieval for banned matches."
  [& {:keys [user-id limit] :or {limit 100} :as args}]
  (if-let [[{:keys [level]}] (find-user-by-id user-id)]
    (if (= level 1)
      (let [usr-prf (first (find-profile :id user-id))
            usr-prf (mapcat #(vector (key %) (val %))
                            (merge usr-prf (hash-args limit)))
            matches (transform-sql-resp (apply find-banned-users usr-prf))]
        {:data matches})
      {:error (check-profile user-id)})))


(comment usage illustation

  (def x (find-all-matches :user-id 6))
  (def y (find-all-favorites :user-id 48))
  (def z (find-all-banned :user-id 48))

  (def x (find-all-matches :user-id 5059))

  (println (str x))


  (def a
    (transform-sql-resp
     (find-users-in-vicinity
      :user_lon 8.7 :user_lat 50.5167 :max-dist 30
      :user_sex "female" :user_interest_sex "male"
      :question_1 1 :question_2 1 :question_3 1
      :question_4 1 :question_5 1 :question_6 1
      :question_7 1 :question_8 1 :question_9 1
      :question_10 1
      :limit 10)))


  (def b
    (transform-sql-resp
     (find-matching-users
      :user_lon 8.7 :user_lat 50.5167 :max-match-variance 150
      :user_sex "female" :user_interest_sex "male"
      :question_1 1 :question_2 1 :question_3 1
      :question_4 1 :question_5 1 :question_6 1
      :question_7 1 :question_8 1 :question_9 1
      :question_10 1
      :limit 50)))

  (def c
    (transform-sql-resp
     (find-recent-users
      :user_lon 8.7 :user_lat 50.5167 :created-before-max-days 30
      :user_sex "female" :user_interest_sex "male"
      :question_1 1 :question_2 1 :question_3 1
      :question_4 1 :question_5 1 :question_6 1
      :question_7 1 :question_8 1 :question_9 1
      :question_10 1
      :limit 10)))

  (count (merge a b c))

  (def d
    (transform-sql-resp
     (find-fav-users
      :user_lon 8.7 :user_lat 50.5167 :id 6
      :user_sex "female" :user_interest_sex "male"
      :question_1 1 :question_2 1 :question_3 1
      :question_4 1 :question_5 1 :question_6 1
      :question_7 1 :question_8 1 :question_9 1
      :question_10 1
      :limit 10)))

  )


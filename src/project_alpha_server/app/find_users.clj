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


(defn- generate-sql-select-exp
  "generates the SQL expression to select database entities
   we include question_4 (are you willing to move again)
   since it gets a special treatment which cannot be
   realized easily with MySQL."
  []
  "SELECT usr.id, usr.name, usr.created_at,prf.question_4,\n")


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

   POW($question_<nr-questions>$ - prf.question_<nr-questions>, 2)

   with the exception of question 4 which gets a special treatment."
  []
  (let [varexp (reduce
                #(str %1 "+" (format "POW($question_%d$-prf.question_%d,2)" %2 %2))
                ""
                (disj (set (range 1 (inc setup/nr-questions))) 4))
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
         < $max_match_variance$"
  []
  (str "\nWHERE " (generate-sql-match-calc-exp) " < $max_match_variance$\n"))


(defn- find-users-in-vicinity
  "requests all zip-positions to the vicinity of a
   given position and distance. For each hit the
   locations zip code, the locations name and the
   distance to the given position is returned."
  [& {:keys [user_lon user_lat max_dist user_sex user_interest_sex limit user-id] :as args}]
  (let [req (str
             (generate-sql-select-exp)
             "ACOS(
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
                    ) * 6380 < $max_dist$
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
             max_match_variance limit user-id] :as args}]
  (let [req (str (generate-sql-select-exp)
                 "ACOS(
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
             question_10 created_before_max_days limit user-id] :as args}]
  (let [req (str (generate-sql-select-exp)
                 "ACOS(
                       SIN(RADIANS(prf.user_lat)) * SIN(RADIANS($user_lat$))
                       + COS(RADIANS(prf.user_lat)) * COS(RADIANS($user_lat$)) * COS(RADIANS(prf.user_lon)
                       - RADIANS($user_lon$))
                       ) * 6380 AS distance,"
                 (generate-sql-match-as-exp)
                 "FROM profiles prf LEFT JOIN users usr ON prf.id = usr.id
                  WHERE DATE_SUB(CURDATE(),INTERVAL $created_before_max_days$ DAY)
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
             created_before_max_days limit] :as args}]
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
             created_before_max_days limit] :as args}]
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


(defn- square [x] (* x x))

(defn- match-variance-for
  "calculate matching score in percent for the 4th question
   'Are you willing to move?'. The first two arguments are
   the answers to this question from both matching parners,
   the third argument is the distance between them.
   When both partners strictly do not want to move and they
   live more than 20 kilometers away from each other set
   the match variance to 100% to indicate that this match
   will never work. Otherwise handle take negatively into
   account when poth partners answer to question-4 with
   3 (do not really like to move) or higher. Worst case is
   that one partners answer with 4 the other with five which
   we currently weight with 20% variance corresponding to
   two totally mismatched answers."
  [question_4_a question_4_b distance]
  (if (> distance 20) ; when potential couple does not live close togehter
    (if (and (= question_4_a 5) (= question_4_b 5))
      100 ; nobody wants definetly to move, this is no hit, 100% variance!
      (if (and (> question_4_a 2) (> question_4_b 2))
        ;; when nobody likes really to move (answer 4,5), weight as two non fitting questions
        (* 0.8 (square (+ (- question_4_a 2) (- question_4_b 2))))
        0))
    0))


(defn- calculate-question-4-match-rate-for
  "The 4th question: 'Are you willing to move?' gets a special treatment.
   Unfortunately the expression to be used is obviously too complex to
   be calculated inside MySQL so we postprocess the database result
   and do not include the calculation for question 4 inside the database.
   Refer to function match-variance-for for more detailed information
   about how we treat this special question."
  [usr-prf matches]
  (let [question_4_a (usr-prf :question_4)]
    (reduce merge
            (map (fn [[id data]]
                   (let [question_4_b (data :question_4)
                         distance (data :distance)
                         match-variance (or (data :match_variance) 100)
                         match-variance (+ match-variance (match-variance-for question_4_a question_4_b distance))
                         match-variance (min match-variance 100)
                         data (assoc data :match_variance match-variance)
                         data (dissoc data :question_4)]
                     {id data}))
                 matches))))


(defn find-all-matches
  "database retrieval for profile matches. Matching is done under several
   aspects where sexual orientation must always match:
   1. users who live in vicinity ('max_dist' kilometers away)
   2. users whose profiles fit best (questionaire variance less than 'max_match_variance')
   3. recently created accounts (after 'created_before-max-days', to allow for tracking new members)
   The results are limited to the values in the profile db keys 'max_hits_vicinity',
   'max_hits_matching' and max_hits_recently_created' and merged afterword.
   The function returns all hits as hash table with the user-id as key in JSON format.
   The client application (ClojureScript) allows to sort the data presented in a table
   format without a new request afterwards."
  [& {:keys [user-id] :as args}]
  (let [[{:keys [level
                 max_dist
                 created_before_max_days
                 max_match_variance
                 max_hits_vicinity
                 max_hits_matching
                 max_hits_recently_created] :as usr-prf}]
        (find-user-profile user-id)]
    (if (= level 1)
      (let [max-var 16
            per2var (fn [var] (* setup/nr-questions max-var (/ var 100.0)))
            max_match_variance (per2var max_match_variance)
            usr-prf (assoc usr-prf :user-id user-id)
            trg-prf (map-sex-interest usr-prf)
            match-prf (mapcat #(vector (key %) (val %))
                              (merge trg-prf (hash-args max_match_variance)))
            matches (map #(transform-sql-resp (apply %1 (conj match-prf %2 :limit)))
                         [find-users-in-vicinity find-matching-users find-recent-users]
                         [max_hits_vicinity max_hits_matching max_hits_recently_created])
            matches (calculate-question-4-match-rate-for usr-prf (reduce merge matches))
            matches (dissoc matches user-id) ; make sure not to integrate the user himself
            ]
        (do
          (update-profile-last-seek-to-now user-id)    ; update time stamp for last seek operation
          (set-new-matches-notifier-for user-id false) ; allow new notification to be sent
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


(defn find-latest-matches-since
  "finds matching users whose profile has been created
   after the given profiles last seek date. This function
   is used for email notification about new matches."
  [profile]
  (sql/select user-profiles
              (sql/join profiles (= :users.id :profiles.id))
              (sql/fields :users.id :users.name :users.created_at :profiles.last_seek)
              (sql/where (= :users.level 1))
              (sql/where {:profiles.user_sex (:user_interest_sex profile)})
              (sql/where {:profiles.user_interest_sex (:user_sex profile)})
              (sql/where (>= :users.created_at (:last_seek profile)))))


(comment usage illustation

  (def x (find-all-matches :user-id 6))
  (def y (find-all-favorites :user-id 48))
  (def z (find-all-banned :user-id 48))

  (def x (find-all-matches :user-id 5059))

  (println (str x))


  (def a
    (transform-sql-resp
     (find-users-in-vicinity
      :user-id 6 :user_lon 8.7 :user_lat 50.5167 :max_dist 30
      :user_sex "female" :user_interest_sex "male"
      :question_1 1 :question_2 1 :question_3 1
      :question_4 1 :question_5 1 :question_6 1
      :question_7 1 :question_8 1 :question_9 1
      :question_10 1
      :limit 10)))


  (def b
    (transform-sql-resp
     (find-matching-users
      :user-id 6 :user_lon 8.7 :user_lat 50.5167 :max_match_variance 150
      :user_sex "female" :user_interest_sex "male"
      :question_1 1 :question_2 1 :question_3 1
      :question_4 1 :question_5 1 :question_6 1
      :question_7 1 :question_8 1 :question_9 1
      :question_10 1
      :limit 50)))

  (def c
    (transform-sql-resp
     (find-recent-users
      :user-id 6 :user_lon 8.7 :user_lat 50.5167 :created_before_max_days 30
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


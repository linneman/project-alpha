;;; project-alpha (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; December 2012, Otto Linnemann
;;;
;;; various tests

(ns project-alpha-server.app.tests
  (:require [korma.db :as db]
            [korma.core :as sql]
            [clojure.java.jdbc :as jdbc]
            [ring.util.codec :as codec]
            [project-alpha-server.local-settings :as setup])
  (:use [project-alpha-server.lib.model]
        [project-alpha-server.app.model]
        [project-alpha-server.app.zip-de]
        [project-alpha-server.lib.utils]
        [macros.macros]))


(defn create-test-user
  "creates a user with the given fiels, see example invocation below"
 [& args]
 (let [fields (apply merge (map #(apply hash-map %) (partition 2 args)))
       user-fields (select-keys fields [:name :email :password :created_at])
       user-fields (assoc user-fields :confirmed 1)
       user-name (:name user-fields)]
   (when (not-empty (find-user :name user-name)) (delete-user :name user-name))
   (let [{id :GENERATED_KEY}
         (apply add-user (mapcat #(vector (key %) (val %)) user-fields))
         profile-fields (dissoc fields :name :email :password :created_at)]
     (update-profile id profile-fields)
     (flush-profile id))))

(comment
  (create-test-user :name "Anton" :email "Otto.Linnemann@google.de" :password "avatar"
                    :text "Some text" :question_1 3 :question_2 4 :question_5 1
                    :user_zip "353" :created_at (java.util.Date. 71 8 2))
  )


;; some male test forenames
(def males ["Paul" "Andreas" "Gert" "Patrick" "Gustav" "Olaf"
            "Ottmar" "Heiner" "Sebastian" "Christoph"
            "Max" "Sepp" "Thorsten" "Karl" "Thomas" "Kai"])

;; some female test forenames
(def females ["Lisa" "Paula" "Gerda" "Sabine" "Monika""Andrea"
              "Patricia" "Anna" "Gudrun""Silke" "Sandy"
              "Helen" "Susanne" "Sandra" "Katarina" "karla"])


(defn names []
  "generates a lazy sequence of names unique names out from
   the given male and female forenames. When more names are
   requested as permutation between forenames and surnames
   exists a counting id is concatenated to the end of the
   name."
  (let [first-names (into males females)
        second-names ["Mueller" "Schmidt" "Bauer" "Schuhmacher"
                      "Stein" "Pfennig" "Baecker" "Schuster"
                      "Bleichert" "Schulz" "Ludwig" "Mai"
                      "Roehl" "Richter" "Hofer" "Kling"
                      "Hauser" "Kaindl" "Kiefer"]
        name-vec (vec (for [first-name first-names second-name second-names]
                        (str first-name " " second-name)))
        nn (count name-vec)
        name-iter (fn [[n next-name]]
                    (let [idx (mod n nn)
                          repeat (quot n nn)
                          next-name (if (= 0 repeat)
                                      (str (name-vec idx))
                                      (str (name-vec idx) (inc repeat)))]
                      [(inc n) next-name]
                      ))]
    (map #(let [[i n] %] n)
         (drop 1 (iterate name-iter [0 nil])))))


(defn zips []
  "creates a lazy-seq of german zip codes"
  (let [zip-vec ["010" "011" "012" "013" "014" "026" "027" "028" "029"
                 "10" "12" "13" "140" "141" "144" "145"
                 "60" "611" "612" "613" "614" "611"
                 "630" "631" "632" "633" "634"
                 "350" "351" "352" "353" "354"
                 "80" "81" "820" "821" "822"
                 "520" "521" "522" "523" "524" "525"
                 "506" "507" "508" "509"]]
    (flatten (repeat zip-vec))))


(defn texts []
  "creates a lazy-seq of user cv texts"
  (let [text-vec
        ["Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Cras et nisi id lorem tempor semper. Suspendisse ante. Integer ligula urna, venenatis quis, placerat vitae, commodo quis, sapien. Quisque nec lectus. Sed non dolor. Sed congue, nisi in pharetra consequat, odio diam pulvinar arcu, in laoreet elit risus id ipsum."
         "Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos hymenaeos. Praesent tellus enim, imperdiet a, sagittis id, pulvinar vel, tortor."
         "Integer nulla. Sed nulla augue, lacinia id, vulputate eu, rhoncus non, ante. Integer lobortis eros vitae quam. Phasellus sagittis, ipsum sollicitudin bibendum laoreet, arcu erat luctus lacus, vel pharetra felis metus tincidunt diam. Cras ac augue in enim ultricies aliquam."]]
    (flatten (repeat text-vec))))


(defn quest-rnk []
  "creates a lazy-seq of question answer codes (1-5)"
  (repeatedly #(inc (int (rand 5)))) )


(defn dates []
  "creates a lazy-seq of dates in the range of the
   last 12 month."
  (let [rand-date
        #(let [millisecs-of-a-year (* 365 24 3600 1000)
               date (java.util.Date.)]
           (.setTime date
                     (- (.getTime date) (rand millisecs-of-a-year)))
           date)]
    (repeatedly rand-date)))


(defn sex-of-name
  "returns the sex of a users full name"
  [full-name]
  (let [[forename surname] (re-seq #"[A-Za-z0-9]+" full-name)
        males (set males)
        females (set females)]
    (condp contains? forename
      males "male"
      females "female"
      (if (> 0.5 (rand 1)) "male" "female"))))


(defn sexes []
  "returns a lazy-seq of random sexes"
  (repeatedly #(if (> 0.5 (rand 1)) "male" "female")))


(defn ages []
  "returns a lazy-seq of random ages between 18 and 48"
  (repeatedly #(+ 18 (int (rand 30)))))


(defn test-users []
  "creates a lazy-seq of test users with the fieds
   given in the functions above."
  (let [gen-email (fn [name]
                (let [[forname surname] (re-seq #"[A-Za-z0-9]+" name)]
                  (str forname "." surname "@avatar.org")))]
    (map (fn [name user_zip text
              question_1 question_2 question_3 question_4 question_5
              question_6 question_7 question_8 question_9 question_10
              created_at user_interest_sex user_age]
           (let [email (gen-email name)
                 user_sex (sex-of-name name)
                 user_country_code "de"
                 password "avatar"]
             (hash-args name email user_sex user_interest_sex user_zip user_country_code text
                        question_1 question_2 question_3 question_4 question_5
                        question_6 question_7 question_8 question_9 question_10
                        user_age created_at password)))
         (names) (zips) (texts)
         (quest-rnk) (quest-rnk) (quest-rnk) (quest-rnk) (quest-rnk)
         (quest-rnk) (quest-rnk) (quest-rnk) (quest-rnk) (quest-rnk)
         (dates) (sexes) (ages))))


(comment

  (take 5 (names))
  ;; ("Paul Mueller" "Paul Schmidt" "Paul Bauer" "Paul Schuhmacher" "Paul Stein")

  (take 5 (drop 100000 (names)))
  ;; ("Gerda Schuhmacher240" "Gerda Stein240" "Gerda Pfennig240" "Gerda Baecker240" "Gerda Schuster240")

  (take 5 (drop 10000000 (names)))
  ;; no out of memory, so the sequence is in fact lazy!

  (take 5 (test-users))
  (take 5 (drop 1000 (test-users)))
  (take 5 (drop 10000 (test-users)))
  (def user (first (test-users)))

  )


(defn create-test-users
  "creates a given number test users and inserts them into the
   database."
  [count]
  (dorun
   (take count
         (map
          (fn [fields]
            (apply create-test-user (mapcat #(vector (key %) (val %)) fields)))
          (test-users)))))


(comment

  (create-test-users 5)
  (create-test-users 5000)

  )


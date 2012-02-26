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
        [project-alpha-server.lib.utils]))


(defn create-test-user
 [& args]
 (let [fields (apply merge (map #(apply hash-map %) (partition 2 args)))
       user-fields (select-keys fields [:name :email :password])
       user-fields (assoc user-fields :confirmed 1)
       user-name (:name user-fields)]
   (when (not-empty (find-user :name user-name)) (delete-user :name user-name))
   (let [{id :GENERATED_KEY}
         (apply add-user (mapcat #(vector (key %) (val %)) user-fields))
         profile-fields (dissoc fields :name :email :password)]
     (update-profile id profile-fields)
     (flush-profile-cache @profile-cache))))


(comment
  (create-test-user :name "Anton" :email "Otto.Linnemann@google.de" :password "avatar"
                    :text "Some text" :question_1 3 :question_2 4 :question_5
                    :zip "353")
  )


(defn create-test-users
  []
  (let [zips ["353" "60" "357" "350" "013"]
        names ["Achim" "Anna" "Karl" "Uta" "Zacharias"]
        user-sex ["male" "female" "male" "female" "male"]
        user-interest-sex ["female" "male" "female" "male" "male"]
        user-age [24 30 35 40 45]
        questions [{:question_1 3 :question_2 4 :question_3 5 :question_4 3 :question_5 2
                    :question_6 3 :question_7 1 :question_8 2 :question_9 1 :question_10 4}
                   {:question_1 4 :question_2 2 :question_3 5 :question_4 3 :question_5 2
                    :question_6 3 :question_7 1 :question_8 2 :question_9 1 :question_10 4}
                   {:question_1 4 :question_2 2 :question_3 2 :question_4 3 :question_5 2
                    :question_6 3 :question_7 1 :question_8 2 :question_9 1 :question_10 4}
                   {:question_1 4 :question_2 2 :question_3 2 :question_4 1 :question_5 2
                    :question_6 3 :question_7 1 :question_8 2 :question_9 1 :question_10 4}
                   {:question_1 4 :question_2 2 :question_3 2 :question_4 1 :question_5 1
                    :question_6 3 :question_7 1 :question_8 2 :question_9 1 :question_10 4}]
        profiles (mapcat
                  #(hash-map %1 (merge
                                 {:user_sex %2 :user_interest_sex %3 :user_age %4} %5))
                  names user-sex user-interest-sex user-age questions)]
    (for [zip zips profile profiles]
      (let [id (first profile)
            name (str id zip)
            email (str name "@googlemail.com")
            profile-fields (val profile)
            fields (merge {:name name :email email :user_zip zip :user_country_code "de"
                           :text "Avatar" :password "avatar"} profile-fields)]
        (apply create-test-user (mapcat #(vector (key %) (val %)) fields))
        ))))

(comment
  (create-test-users)
  )


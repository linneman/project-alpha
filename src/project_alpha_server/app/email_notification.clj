;;; project-alpha (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the GNU General Public License
;;;
;;; March 2013, Otto Linnemann

(ns project-alpha-server.app.email-notification
  (:require [korma.db :as db]
            [korma.core :as sql]
            [clojure.java.jdbc :as jdbc]
            [ring.util.codec :as codec]
            [project-alpha-server.local-settings :as setup]
            [clojure.string :as string])
  (:use [project-alpha-server.app.model]
        [project-alpha-server.lib.model]
        [project-alpha-server.app.find-users]
        [clojure.data.json :only [json-str write-json read-json]]
        [project-alpha-server.lib.utils]
        [project-alpha-server.lib.email])
  (:import [java.text DateFormat SimpleDateFormat]
           [java.util TimerTask Timer Calendar Date]))


(defn get-inactive-users
  "returns the id, name and email for all users who have not
   seeked for matching users since given since-date
   which should be last notification since-date."
  [since-date]
  (sql/select user-profiles
              (sql/join profiles (= :users.id :profiles.id))
              (sql/fields :users.id :users.name :users.email :profiles.lang)
              (sql/where (= :users.level 1))
              (sql/where (= :profiles.mail_new_matches true))
              (sql/where (< :profiles.last_seek since-date))))


(defn get-users-with-new-mail
  "returns the id, name and email for all users with new mail."
  []
  (sql/select users-profiles-with-unread-messages
              (sql/join profiles (= :users.id :profiles.id))
              (sql/join unread_messages (= :users.id :unread_messages.user_id))
              (sql/fields :users.id :users.name :users.email :profiles.lang)
              (sql/where (= :users.level 1))
              (sql/where (> :unread_messages.msg_id 0))))


(defn- iterate-userlist-and-trigger-for-new-matches
  "iterates through list of given users and checks whether
   whether there are new matches since given date. If so
   invokes notifier function which is in charge for sending
   out the notification mail about new matches."
  [inactive-user-list matches-since-date mail-fn]
  (doseq [user inactive-user-list]
    (let [{:keys [id name email lang]} user
          [user-sex-map]
          (sql/select profiles
                      (sql/fields :user_sex :user_interest_sex)
                      (sql/where (= :id id)))]
      (when-not
        (or ; notify only when there are new matches and we haven not mailed yet
         (empty? (find-latest-matches-since matches-since-date user-sex-map))
         (is-user-notified-about-new-matches? id))
        (mail-fn {:name name :id id :email email :lang lang})
        (set-new-matches-notifier-for id true)
        ))))


(defn- iterate-userlist-and-trigger-for-new-messages
  "iterates through list of given users and invokes notifier
   function which is in charge for sneding out notification
   mail about unread messages."
  [users-with-unread-messages mail-fn]
  (doseq [user users-with-unread-messages]
    (let [{:keys [id name email lang]} user]
      (when-not (is-user-notified-about-new-mail? id)
        (mail-fn {:name name :id id :email email :lang lang})
        (set-new-mail-notifier-for id true))
      )))


(comment usage-illustration

         (def inactive-before-date
           (java.util.Date.
            (- (. (java.util.Date.) getTime) (* 1 3600 24 1000))))

         (def matches-after-date
           (java.util.Date.
            (- (. (java.util.Date.) getTime) (* 2 3600 24 1000))))

         (defn- mail-logger [{:keys [name id email lang]}]
           (println "sent out email to user-id" id "named" name ", address:" email ", lang: " lang))

         (take 5 (get-inactive-users inactive-before-date))

         (iterate-userlist-and-trigger-for-new-matches
          (take 5 (get-inactive-users inactive-before-date))
          matches-after-date
          mail-logger)

         (iterate-userlist-and-trigger-for-new-messages (get-users-with-new-mail) mail-logger)
         )


(defn get-email-subs-and-body
  "reads out email subject and body strings from files system."
  [lang subj-path msg-path]
  [(slurp (replace-dollar-template-by-keyvals subj-path {:lang lang}))
   (slurp (replace-dollar-template-by-keyvals msg-path {:lang lang}))])

;; memoization is used for caching here to avoid multiple read access of same file
(def get-email-subs-and-body (memoize get-email-subs-and-body))


(defn send-new-matches-mail
  "sends the user with the specified address an email
   informing him/her about new potential matches."
  [{:keys [name id email lang]}]
  (let [subj-path setup/new-matches-email-subj-path
        msg-path setup/new-matches-email-msg-path
        [subj msg] (get-email-subs-and-body lang subj-path msg-path)
        msg (str msg setup/host-url lang "/search.html")]
    (println "send out new mail to: " email ", subject:" subj ", msg:" msg)
    (when-not (re-seq #"@avatar\.org$" email)
      (sendmail email subj msg))))


(defn send-new-messages-mail
  "sends the user with the specified address an email
   informing him/her about new potential matches."
  [{:keys [name id email lang]}]
  (let [subj-path setup/new-messages-email-subj-path
        msg-path setup/new-messages-email-msg-path
        [subj msg] (get-email-subs-and-body lang subj-path msg-path)
        msg (str msg setup/host-url lang "/status.html")]
    (println "send out new mail to: " email ", subject:" subj ", msg:" msg)
    (when-not (re-seq #"@avatar\.org$" email)
      (sendmail email subj msg))))


(defn- get-next-trigger-date
  "defines next date and clock when to start the
   email notification background task."
  []
  (let [next-trigger-date (. Calendar (getInstance))]
    (.set next-trigger-date Calendar/HOUR_OF_DAY 1)
    (.set next-trigger-date Calendar/MINUTE 0)
    (.set next-trigger-date Calendar/SECOND 0)
    (.add next-trigger-date Calendar/DAY_OF_MONTH 1)
    next-trigger-date))


(defn- get-last-trigger-date
  "defines the date when last notification email
   has been sent out."
  []
  (let [last-trigger-date (. Calendar (getInstance))]
    (.add last-trigger-date Calendar/DAY_OF_MONTH -1)
    last-trigger-date))


(defn notify-about-new-matches
  "detect inactive users, check whether there are new matches and
   whether they are not yet informed about them. In this case enqueue
   the notification email to these users and set the flag about
   new-matches-email-has-been-sent to true. This flag is cleared
   whenever the user fetches the latest matching list via the
   'find' function in the web interface."
  []
  (let [last-notification-date (get-last-trigger-date)
        inactive-users (get-inactive-users (.getTime last-notification-date))]
    (iterate-userlist-and-trigger-for-new-matches
     inactive-users
     (.getTime last-notification-date)
     send-new-matches-mail)))


(defn notify-about-new-messages
  "sends out a notification email to all users who have
   received new respectively unread messages."
  []
  (iterate-userlist-and-trigger-for-new-messages (get-users-with-new-mail) send-new-messages-mail))



(defn- get-test-trigger-date
  []
  (let [test-trigger-date (. Calendar (getInstance))]
       ;(.set test-trigger-date Calendar/HOUR_OF_DAY 1)
       (.set test-trigger-date Calendar/MINUTE 28)
       (.set test-trigger-date Calendar/SECOND 0)
       test-trigger-date))



(defn start-email-notification-timer
  "starts a timer triggers the sending of notification
   email once a day around midnight"
  []
  (let [task (proxy [TimerTask] []
               (run []
                 (println "start-email-notification-timer triggered")
                 (when setup/email-notify-new-matches (notify-about-new-matches))
                 (when setup/email-notify-new-messages (notify-about-new-messages))))]
    (println "start-email-notification-timer running")
    (def email-notification-timer (new Timer))
    (. email-notification-timer
       (scheduleAtFixedRate task
                          (.getTime (get-next-trigger-date))
                          ;(.getTime (get-test-trigger-date))
                          (long (* 1000 3600 24))))))


(defn stop-email-notification-timer
  "stops notification timer"
  []
  (.cancel email-notification-timer))

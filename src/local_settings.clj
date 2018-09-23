;;; project-alpha (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the GNU General Public License
;;;
;;; December 2011, Otto Linnemann

(ns local-settings)


;;; --- Port and domain setup ---

(def http-port 8000)
(def https-port 3443)
(def use-https false)
(def port (if use-https https-port http-port))
(def host "https://central-services.dnsdojo.org")
(def base-url "/project-alpha/")
(def host-url (str host base-url))

(def jetty-setup
  {:port port
   :join? false
   :ssl? use-https
   :ssl-port https-port
   :keystore "resources/keys/key_crt.jks"
   :key-password "password"})

(def cookie-max-age (* 30 24 3600))


(def ^{:doc "when true display maintenance page"} maintencance-mode false)


;;; --- Supported languages for localized html (resources/templates) ---

(def languages #{"de" "en"}) ;; supported languages currently German and English
(def default-language "en")  ;; default language when browser reports other language than supported


;;; --- Definitions for send mail, currently only GMail has been tested ---

(def email-host-name "smtp.gmail.com")
(def email-ssl-smtp-port "465")
(def email-set-ssl true)
(def email-from-name "project alpha")
(def email-from-email "projectalpha46@gmail.com")
(def email-auth-name "projectalpha46@gmail.com")
(def email-auth-password "password")

(def email-notify-new-matches true)
(def email-notify-new-messages true)


;;; --- Local files e.g. used for sendmail ---

(def email-authentication-required true)
(def confirm-email-msg-path "resources/templates/$lang$/confirm_email_msg.txt")
(def confirm-email-subj-path "resources/templates/$lang$/confirm_email_subj.txt")
(def reset-pw-email-msg-path "resources/templates/$lang$/reset_pw_email_msg.txt")
(def reset-pw-email-subj-path "resources/templates/$lang$/reset_pw_email_subj.txt")
(def new-matches-email-msg-path "resources/templates/$lang$/new_matches_email_msg.txt")
(def new-matches-email-subj-path "resources/templates/$lang$/new_matches_email_subj.txt")
(def new-messages-email-msg-path "resources/templates/$lang$/new_messages_email_msg.txt")
(def new-messages-email-subj-path "resources/templates/$lang$/new_messages_email_subj.txt")

;;; --- profile setup ---
(def ^{:doc "questionaire from question_1 .. question_<nr-questions>"} nr-questions 10)


;;; --- Connection parameters to SQL database, currently MySQL ---

(def sql-connection
  {:db "project-alpha"
   :host "localhost"
   :port 3306
   :user "project-alpha"
   :password "password"})


;;; --- Conection to German zip location database, currently MySQL ----
;;; taken from http://opengeodb.org

(def sql-connection-opengeodb-de
  {:db "opengeodb_de"
   :host "localhost"
   :port 3306
   :user "project-alpha"
   :password "password"})

;;; project-alpha (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; December 2011, Otto Linnemann

(ns project-alpha-server.local-settings)

(def host-url "http://localhost:3000/")
(def cookie-max-age (* 30 24 3600))

;;; --- Supported languages for localized html (resources/templates) ---
(def languages #{"de" "en"})
(def default-language "de")


;;; --- Definitions for send mail, currently only GMail has been tested ---

(def email-host-name "smtp.gmail.com")
(def email-ssl-smtp-port "465")
(def email-set-ssl true)
(def email-from-name "project alpha")
(def email-from-email "projectalpha42@gmail.com")
(def email-auth-name "projectalpha42@gmail.com")
(def email-auth-password "projectalpha42")


;;; --- Local files e.g. used for sendmail ---

(def email-authentication-required true)
(def confirm-email-msg-path "resources/templates/$lang$/confirm_email_msg.txt")
(def confirm-email-subj-path "resources/templates/$lang$/confirm_email_subj.txt")
(def reset-pw-email-msg-path "resources/templates/$lang$/reset_pw_email_msg.txt")
(def reset-pw-email-subj-path "resources/templates/$lang$/reset_pw_email_subj.txt")


;;; --- Connection parameters to SQL database, currently MySQL ---

(def sql-connection
  {:db "project-alpha"
   :host "localhost"
   :port 8889
   :user "project-alpha"
   :password "test"})


;;; --- Conection to German zip location database, currently MySQL ----
;;; taken from http://opengeodb.org

(def sql-connection-opengeodb-de
  {:db "opengeodb_de"
   :host "localhost"
   :port 8889
   :user "project-alpha"
   :password "test"})

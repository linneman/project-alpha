;;; project-alpha (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the GNU General Public License
;;;
;;; taken from William Groppe, many thanks for contributing this!
;;; http://will.groppe.us/post/406065542/sending-email-from-clojure
;;;
;;; December 2011, Otto Linnemann


(ns project-alpha-server.lib.email
  (:use [project-alpha-server.lib.utils])
  (:require [project-alpha-server.local-settings :as setup])
  (:import [org.apache.commons.mail SimpleEmail]))


(defn sendmail
  "sends an email to the given address with the
   setup data in local_settings.clj."
  [to-address subject message]
  (doto (SimpleEmail.)
    (.setHostName setup/email-host-name)
    (.setSslSmtpPort setup/email-ssl-smtp-port)
    (.setSSL setup/email-set-ssl)
    (.addTo to-address)
    (.setFrom setup/email-from-email setup/email-from-name)
    (.setSubject subject)
    (.setCharset "UTF8")
    (.setMsg message)
    (.setAuthentication setup/email-auth-name setup/email-auth-password)
    (.send)))

; (sendmail "linneman@gmx.de" "Test" "Eine Nachricht mit Umlauten: ÄÖÜ äöü ß: http://central-services.dnsdojo.org")

(defn send-confirm-mail
  "sends the user with the specified address an email
   with the url he has to confirm his registration."
  [lang to-address url]
  (let [msg (slurp (replace-dollar-template-by-keyvals setup/confirm-email-msg-path
                                                       {:lang lang}))
        subj (slurp (replace-dollar-template-by-keyvals setup/confirm-email-subj-path
                                                       {:lang lang}))]
    (sendmail to-address subj (str msg url))))

(defn send-reset-passwd-mail
  "sends the user with the specified address an email
   with the url for allowing to reset the password."
  [lang to-address url]
  (let [msg (slurp (replace-dollar-template-by-keyvals setup/reset-pw-email-msg-path
                                                       {:lang lang}))
        subj (slurp (replace-dollar-template-by-keyvals setup/reset-pw-email-subj-path
                                                       {:lang lang}))]
    (sendmail to-address subj (str msg url))))

; (send-confirm-mail "linneman@gmx.de" "/confirm/abcdef")



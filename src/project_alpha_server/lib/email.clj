;;; project-alpha (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; taken from William Groppe, many thanks for contributing this!
;;; http://will.groppe.us/post/406065542/sending-email-from-clojure
;;;
;;; December 2011, Otto Linnemann


(ns project-alpha-server.lib.email
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
  [to-address url]
  (let [msg (slurp setup/confirm-email-msg-path)
        subj (slurp setup/confirm-email-subj-path)]
    (sendmail to-address subj (str msg url))))

(defn send-reset-passwd-mail
  "sends the user with the specified address an email
   with the url for allowing to reset the password."
  [to-address url]
  (let [msg (slurp setup/reset-pw-email-msg-path)
        subj (slurp setup/reset-pw-email-subj-path)]
    (sendmail to-address subj (str msg url))))

; (send-confirm-mail "linneman@gmx.de" "/confirm/abcdef")



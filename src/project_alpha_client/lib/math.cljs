;;;
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the GNU General Public License
;;;
;;; dialog box with information about user details
;;;
;;; 2012-04-11, Otto Linnemann

(ns project-alpha-client.lib.math)

(def ceil (.-ceil js/Math))
(def floor (.-floor js/Math))
(def sqrt (.-sqrt js/Math))
(def abs (.-abs js/Math))

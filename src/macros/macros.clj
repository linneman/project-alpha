;;;
;;; Clojure based web application
;;; https://github.com/clojure/clojurescript for further information.
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; ====== utility macros ======
;;;
;;; 2012-04-08, Otto Linnemann


(ns macros.macros)

(defmacro hash-args
  "constructs a hash map with given arguments is value
   and the corresponding keywords as keys."
  [& symbols]
  (doall (reduce #(assoc %1 (keyword %2) %2) {} symbols)))


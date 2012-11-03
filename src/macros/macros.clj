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
   and the corresponding keywords as keys.
   example:  (let [a 42 b 43] (hash-args a b))
          -> {:b 43, :a 42}"
  [& symbols]
  (doall (reduce #(assoc %1 (keyword %2) %2) {} symbols)))



(defmacro apply-hash
  "like apply but uses a hash list instead of a vector
   example (f :a 42 :b 43) is equivalent to
           (apply-hash {:a 42 :b 43} f)"
    [h f]
    `(apply ~f (mapcat #(vector (key %) (val %)) ~h)))

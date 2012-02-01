;;; project-alpha (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; December 2011, Otto Linnemann
;;;
;;; utility functions

(ns project-alpha-server.lib.utils)



(defn json2clj-hash
  "transforms a clojurescript (json) hash
   to hash where the keys are keywords as
   usual in clojure.

   Example (json2clj-hash {'a' 42 'b' 43})
            -> {:a 42 :b 43}."
  [h]
  (zipmap (map keyword (keys h)) (vals h)))


(defn clj2json-hash
  "transforms a json hash with keyword keys
   to a clojurescript (json) hash

   Example (json2clj-hash {:a 42 :b 43})
            -> {'a' 42 'b' 43}."
  [h]
  (let [kw2str (fn [kw] (apply str (rest (str kw))))]
    (zipmap (map kw2str (keys h)) (vals h))))

;;; project-alpha (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; December 2011, Otto Linnemann
;;;
;;; utility functions

(ns project-alpha-server.lib.utils
  (:require [clojure.string :as string]))


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


(defn replace-dollar-template-by-keyvals
  "takes a string with template values prefixed
   with a dollar sign and replaces all templates
   for the given keys with the given values in
   the hash table kv."
  [s kv]
  (let [key2str (fn [k] (. (str k) (substring 1)))
        var-repl (fn [s k v]
                   (string/replace s (str "$" (key2str k) "$") v))]
    (loop [l kv repl s]
              (if (empty? l)
                repl
                (let [kv (first l) k (key kv) v (val kv)
                      repl (var-repl repl k (str v))]
                  (recur (rest l)
                         repl))))))


(defn forward-url
  "utility function for forwarding to given url."
  [url]
  (format "<html><head><meta  http-equiv=\"refresh\" content=\"0; URL=%s\"></head><body>forwarding ...</body></html>" url))


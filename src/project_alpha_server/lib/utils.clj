;;; project-alpha (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the GNU General Public License
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


(defn err-println
  "println for STDERR"
  [& args]
  (binding [*out* *err*]
    (apply println args)))


(defn sql-resp-transform-date
  "trim date string to year-month-day"
  [sql-res key]
  (map
   #(assoc-in % [key] (. (str (% key)) substring 0 10))
   sql-res))


(defn sql-resp-transform-to-german-date
  "trim date string to day.month.year"
  [sql-res key]
  (map
   #(assoc-in % [key]
              (let [us-date-str (str (% key))
                    year (. us-date-str substring 0 4)
                    month (. us-date-str substring 5 7)
                    day (. us-date-str substring 8 10)]
                (str day "." month "." year)))
   sql-res))


(defn sql-resp-2-hash-by-id
  "transforns the sql response data set to a hash set
   where the id is assigned to the key and all other
   entries are assigned to the values."
  [sql-res key]
  (reduce #(assoc %1 (%2 key) (dissoc %2 key)) {} sql-res))

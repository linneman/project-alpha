;;; project-alpha (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the Eclipse Public License 1.0, the same as clojure
;;;
;;; December 2011, Otto Linnemann

;;; example used from the net, many thanks to:
;;; http://en.wikibooks.org/wiki/Clojure_Programming/Examples/JDBC_Examples
;;; http://sqlkorma.com
;;; https://gist.github.com/1521214

;;; location services for german zip codes

(ns project-alpha-server.app.zip-de
  (:require [korma.db :as db]
            [korma.core :as sql]
            [clojure.java.jdbc :as jdbc]
            [project-alpha-server.local-settings :as setup])
  (:use [project-alpha-server.lib.model]
        [clojure.data.json :only [json-str write-json read-json]]
        [project-alpha-server.lib.utils]))


;; The database connection
;; argument for sql requests
(def opengeodb-con (db/mysql setup/sql-connection-opengeodb-de))



;;; --- utility functions ---

(defn opengeo-sqlreq
  "sends an sql request to the database
   example:
    (sqlreq 'select count(*) from users')"
  [req]
  (jdbc/with-connection opengeodb-con
    (jdbc/with-query-results rs
      [req]
      (doall rs))))



(defn- get-location
  "retrieves the location entry with the highest population
   which matches the given zip from the geodb database.
   this query is based from:
   http://www.guido-muehlwitz.de/2009/10/geocodieren-von-postleitzahlen-mit-der-opengeodb"
  [zip]
  (first
   (opengeo-sqlreq
    (format
     "SELECT gl.loc_id, plz.text_val, name.text_val, coord.lat, coord.lon, data.int_val
         FROM geodb_textdata plz
         LEFT JOIN geodb_textdata name ON name.loc_id = plz.loc_id
         LEFT JOIN geodb_locations gl ON gl.loc_id = plz.loc_id
         LEFT JOIN geodb_coordinates coord ON plz.loc_id = coord.loc_id
         LEFT JOIN geodb_intdata data ON plz.loc_id = data.loc_id
         WHERE plz.text_type =500300000
         AND plz.text_val LIKE '%s%%'
         AND name.text_type =500100000
         AND gl.loc_type =100600000
         AND data.int_type =600700000
         ORDER BY data.int_val DESC;", zip))))


(defn get-location-for-zip
  "returns longitude and latitude for a given zip.
   uses method get-location for calculation."
  [zip]
  (let [location (get-location zip)
        lon (:lon location)
        lat (:lat location)]
    {:user_lon lon :user_lat lat}))



(defn- deg2rad
  "returns angle in radians"
  [deg]
  (/ (* deg  (Math/PI)) 180))


(defn calc-dist-long-lat
  "returns distance between two earth locations"
  [lon1 lat1 lon2 lat2]
  (let [angle
        (Math/acos
          (+
           (* (Math/sin (deg2rad lat1)) (Math/sin (deg2rad lat2)))
           (* (Math/cos (deg2rad lat1))
              (Math/cos (deg2rad lat2))
              (Math/cos (deg2rad (- lon1 lon2))))))
        radius-of-earth 6378.388]
    (* angle radius-of-earth)))

(defn- calc-dist-zip
  [zip1 zip2]
  (let [loc1 (get-location zip1)
        loc2 (get-location zip2)]
    (calc-dist-long-lat (:lon loc1) (:lat loc1) (:lon loc2 ) (:lat loc2))))



(comment
  ; Giessen - Frankfurt
  (calc-dist-zip "60" "353")

  (def marburg "350")
  (def giessen "353")
  (calc-dist-zip giessen marburg))

;;; project-alpha (server-side)
;;;
;;; The use and distribution terms for this software are covered by
;;; the GNU General Public License
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
            [local-settings :as setup]
            [clojure.string :as string])
  (:use [clojure.data.json :only [json-str write-json read-json]]
        [project-alpha-server.lib.utils]))


;; The database connection
;; argument for sql requests
(def opengeodb-con (db/mysql setup/sql-connection-opengeodb-de))


;;; --- utility functions ---
;;; (opengeodb-de is required)

(defn opengeo-sqlreq
  "sends an sql request to the database"
  [req]
  (jdbc/with-connection opengeodb-con
    (jdbc/with-query-results rs
      [req]
      (doall rs))))

(defn opengeo-sqltact
  "trigger sql transaction"
  [transaction]
  (jdbc/with-connection opengeodb-con
    (jdbc/transaction (transaction))))

(defn opengeo-sqltcmd
  "trigger sql transaction"
  [cmd]
  (jdbc/with-connection opengeodb-con
    (jdbc/do-commands cmd)))

(defn create-geodb-table
  "Create table with given spec"
  [table & spec]
  (opengeo-sqltact #(apply jdbc/create-table (conj spec table))))

(defn drop-geodb-table
  "Drop the specified table"
  [table]
  (jdbc/with-connection opengeodb-con
    (jdbc/transaction
     (try
       (clojure.java.jdbc/drop-table table)
       (catch Exception _)))))

;; -- database setup  --

(defn create-table-zip-coordinates
  "create table for zip coordinates"
  []
  (create-geodb-table
   :zip_coordinates
   [:zc_id :integer "NOT NULL" "PRIMARY KEY" "AUTO_INCREMENT"]
   [:zc_loc_id :integer "NOT NULL"]
   [:zc_zip "varchar(10)" "NOT NULL"]
   [:zc_location_name "varchar(255)" "NOT NULL"]
   [:zc_lat :double "NOT NULL"]
   [:zc_lon :double "NOT NULL"]
   ))

(defn import-table-zip-coordinates
  "inserts required values into table zip-coordinates"
  []
  (opengeo-sqltcmd
   "INSERT INTO zip_coordinates (zc_loc_id, zc_zip, zc_location_name, zc_lat, zc_lon)
      SELECT gl.loc_id, plz.text_val, name.text_val, coord.lat, coord.lon
         FROM geodb_textdata plz
         LEFT JOIN geodb_textdata name ON name.loc_id = plz.loc_id
         LEFT JOIN geodb_locations gl ON gl.loc_id = plz.loc_id
         LEFT JOIN geodb_coordinates coord ON plz.loc_id = coord.loc_id
         LEFT JOIN geodb_intdata data ON plz.loc_id = data.loc_id
         WHERE plz.text_type =500300000
         AND name.text_type =500100000
         AND gl.loc_type =100600000
         AND data.int_type =600700000;"))



(comment
  ;; Zip - Coordinate table must be created
  (create-table-zip-coordinates)

  ;; and appropriate values must be inserted
  ;; for vicinity search
  (import-table-zip-coordinates)

  ;; drops the table
  (drop-geodb-table :zip_coordinates)
  )


;;; --- API ---

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



(defn get-vicinity-to-pos
  "requests all zip-positions to the vicinity of a
   given position and distance. For each hit the
   locations zip code, the locations name and the
   distance to the given position is returned."
  [lon lat dist]
  (let [req "SELECT
               zc_zip,
               zc_location_name,
               ACOS(
                    SIN(RADIANS(zc_lat)) * SIN(RADIANS($lat))
                    + COS(RADIANS(zc_lat)) * COS(RADIANS($lat)) * COS(RADIANS(zc_lon)
                    - RADIANS($lon))
                    ) * 6380 AS distance
               FROM zip_coordinates
               WHERE ACOS(
                    SIN(RADIANS(zc_lat)) * SIN(RADIANS($lat))
                    + COS(RADIANS(zc_lat)) * COS(RADIANS($lat)) * COS(RADIANS(zc_lon)
                    - RADIANS($lon))
                    ) * 6380 < $dist
               ORDER BY distance;"
        req (string/replace req "$lon" (str lon))
        req (string/replace req "$lat" (str lat))
        req (string/replace req "$dist" (str dist))
        ]
    (opengeo-sqlreq req)
    ))


(defn get-vicinity-to-zip
  "requests all zip-positions to the vicinity of a
   given zip code distance. For each hit the
   locations zip code, the locations name and the
   distance to the given position is returned."
  [zip distance]
  (let [{:keys [user_lon user_lat]} (get-location-for-zip zip)]
    (get-vicinity-to-pos user_lon user_lat distance)))


(comment

  (get-vicinity-to-pos 8.7 50.5167 10) ; giessen
  (get-vicinity-to-zip "354" 10) ; giessen

  )

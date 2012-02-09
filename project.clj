(defproject project-alpha "1.0.0-SNAPSHOT"
  :description "clojure based web application"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [ring/ring-core "0.3.11"]
                 [ring/ring-jetty-adapter "0.3.11"]
                 [ring-json-params "0.1.3"]
                 [compojure "0.6.5" :exclusions
                  [org.clojure/clojure org.clojure/clojure-contrib]]
                 [org.ol42/enlive "1.3.0-corfield-alpha1"]
                 [korma "0.2.2-SNAPSHOT"]
                 [mysql/mysql-connector-java "5.1.6"]
                 [org.clojure/java.jdbc "0.1.1"]
                 [org.clojure/data.json "0.1.1"]
                 [org.apache.commons/commons-email "1.2"]]
  :dev-dependencies [[lein-clojurescript "1.1.0"]]
  :main project-alpha-server.app.core)

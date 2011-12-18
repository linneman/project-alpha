(defproject project-alpha "1.0.0-SNAPSHOT"
  :description "clojure based web application"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [ring/ring-core "0.3.11"]
                 [ring/ring-jetty-adapter "0.3.11"]
                 [compojure "0.6.5" :exclusions
                  [org.clojure/clojure org.clojure/clojure-contrib]]]
  :dev-dependencies [[lein-clojurescript "1.0.1-SNAPSHOT"]]
  :main project-alpha-server.core)

(defproject project-alpha "1.0.0-SNAPSHOT"
  :description "clojure based web application"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [ring/ring-core "1.1.0"]
                 [ring/ring-jetty-adapter "1.1.0"]
                 [ring-json-params "0.1.3"]
                 [compojure "0.6.5" :exclusions
                  [org.clojure/clojure org.clojure/clojure-contrib]]
                 [org.ol42/enlive "1.3.0-corfield-alpha1"]
                 [korma "0.3.0-RC2"]
                 [mysql/mysql-connector-java "5.1.6"]
                 [org.clojure/java.jdbc "0.2.2"]
                 [org.clojure/data.json "0.1.1"]
                 [org.apache.commons/commons-email "1.2"]
                 [swank-clojure "1.4.3"]
                 ]
  :plugins [[lein-cljsbuild "0.3.0"]
            [lein-ring "0.7.0"]]
  :source-paths ["src"
                 "clojurescript/src/clj"
                 "clojurescript/src/cljs"
                 ]
  :hooks [leiningen.cljsbuild]
  :cljsbuild {
              :repl-listen-port 9000
              :repl-launch-commands
                                        ; Launch command for connecting the page of choice to the REPL.
                                        ; Only works if the page at URL automatically connects to the REPL,
                                        ; like http://localhost:3000/repl-demo does.
                                        ;     $ lein trampoline cljsbuild repl-launch firefox <URL>
              {"firefox" ["firefox"
                          :stdout ".repl-firefox-out"
                          :stderr ".repl-firefox-err"]
                                        ; Launch command for interacting with your ClojureScript at a REPL,
                                        ; without browsing to the app (a static HTML file is used).
                                        ;     $ lein trampoline cljsbuild repl-launch firefox-naked
               }
              :crossovers [macros]
              :crossover-jar true
              :builds {:prod
                       {:source-paths ["src/project_alpha_client"]
                        :compiler {:output-to "resources/public/project_alpha_optimized.js"
                                   :optimizations :advanced
                                   :pretty-print false}}
                       :debug
                       {:source-paths ["src/project_alpha_client"]
                        :compiler {:output-to "resources/public/project_alpha.js"
                                   :optimizations :whitespace
                                   :pretty-print true}}
                       }}
  :main project-alpha-server.app.core)

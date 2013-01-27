;;; repl for interactive development
;;; only invoked when no optimizations used

(ns project-alpha-client.lib.repl
  (:require [clojure.browser.repl :as repl]
            [goog.Timer :as timer])
  (:use [project-alpha-client.lib.logging :only [loginfo]])
  )

(comment defn ^:export repl
  "Connects to a ClojureScript REPL running on localhost port 9000.

  This allows a browser-connected REPL to send JavaScript to the
  browser for evaluation. This function should be called from a script
  in the development host HTML page."
  []
  (repl/connect (str (server) ":9000/repl")))


(comment (timer/callOnce #(do (loginfo "******* NOW START REPL *******")
                              (repl/connect "http://localhost:9000/repl"))
                         (* 10 1000)))

(defn ^:export connect []
  (repl/connect "http://localhost:9000/repl"))

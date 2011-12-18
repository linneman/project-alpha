;;; repl for interactive development
;;; only invoked when no optimizations used

(ns project-alpha-client.repl
  (:require
   [project-alpha-client.core :as core]
   [clojure.browser.repl :as repl]))

(let [compiled (js* "(function() { return COMPILED; })();")]
  (if (not compiled)
    (repl/connect "http://localhost:9000/repl")))

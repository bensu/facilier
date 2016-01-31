(ns facilier.main
  (:require [com.stuartsierra.component :as component]
            reloaded.repl)
  (:gen-class))

(defn init
  ([] (init nil))
  ([opts]
   (require 'facilier.server)
   ((resolve 'facilier.server/new-system) opts)))

(defn setup-app! [opts]
  (reloaded.repl/set-init! #(init opts)))

(defn -main [& args]
  (component/start (init)))

(ns facilier.helper
  "Load edn from files while compiling cljs"
  (:require [clojure.edn :as edn]))

(defn load-edn-helper
  "Reads a file and returns it as a string.
  It throws an exception if the file is not found."
  [relative-path]
  (edn/read-string (slurp relative-path)))

(defmacro load-edn
  "Reads a file and returns it as a string.
  It throws an exception if the file is not found."
  [relative-path]
  (edn/read-string (slurp relative-path)))

(defmacro load-states [app-name]
  (let [path (str "test/resources/predictive/states/" app-name ".edn")
        edn-string (load-edn-helper path)]
    `(mapv cljs.reader/read-string ~edn-string)))

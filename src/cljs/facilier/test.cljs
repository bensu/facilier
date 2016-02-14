(ns facilier.test)

(def ^:dynamic test?)

(defn start-testing! []
  (set! test? true))

(defn stop-testing! []
  (set! test? false))

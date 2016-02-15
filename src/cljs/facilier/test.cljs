(ns facilier.test
  (:import [goog.ui IdGenerator]))

;; ======================================================================
;; Effects flag

(def ^:dynamic test?)

(defn start-testing! []
  (set! test? true))

(defn stop-testing! []
  (set! test? false))

;; ======================================================================
;; Event Handlers

(def handlers (atom {}))

(def *id-gen* (IdGenerator.))

(defn next-id! []
  (.getNextUniqueId *id-gen*))

(defn add-handler! [id f]
  (swap! handlers #(assoc % id f)))

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

(def handlers
  "Registry with all loaded event handlers"
  (atom {}))

;; It's important that we use a *deterministic* id generator
(def *id-gen* (IdGenerator.))

(defn next-id! []
  (.getNextUniqueId *id-gen*))

(defn add-handler!
  "Adds an event handler to the registry"
  [id f]
  (swap! handlers #(assoc % id f)))

;; Should it be a *serialized* event?
(defn replay!
  "Replays a serialized event agains the current DOM"
  [e]
  (let [e (.parse js/JSON e)
        f (get @handlers (aget e "_handlerId"))]
    (assert (fn? f))
    (f e)))

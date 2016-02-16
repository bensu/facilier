(ns facilier.test
  (:import [goog.ui IdGenerator])
  (:require [cljs.reader :as reader]))

;; ======================================================================
;; Effects flag

(def ^:dynamic test?)

(defn start-testing! []
  (set! test? true))

(defn stop-testing! []
  (set! test? false))

;; ======================================================================
;; Event Handlers

(defn edn? ^boolean [obj]
  (contains? #{PersistentArrayMap PersistentHashMap PersistentVector
               PersistentHashSet PersistentTreeSet}
             (type obj)))

(defn read-event [event]
  (cond-> event
    (contains? event :event/json) (update :event/json #(.parse js/JSON %))
    (contains? event :event/edn) (update :event/edn reader/read-string)))

;; Should be a *deterministic* id generator
(defn new-registry []
  {:id-gen (IdGenerator.)
   :fns {}})

(def handler-registry
  "Registry with all loaded event handlers"
  (atom (new-registry)))

(defn next-id! []
  (.getNextUniqueId (:id-gen @handler-registry)))

(defn add-handler!
  "Adds an event handler to the registry"
  [id f]
  (swap! handler-registry #(assoc-in % [:fns id] f)))

(defn unmount! []
  (reset! handler-registry (new-registry)))

(defn unwrap-event [e]
  (or (:event/json e) (:event/edn e)))

;; Should it be a *serialized* event?
(defn replay!
  "Replays a serialized event agains the current DOM"
  [e]
  (let [e (read-event e)
        f (get-in @handler-registry [:fns (:handler/id e)])]
    (assert (fn? f) (str "Expected function found: " (type f)))
    (f (unwrap-event e))))

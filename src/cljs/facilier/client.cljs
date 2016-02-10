(ns facilier.client
  "Helpers to log states from the client"
  (:require [cljs.reader :as reader]
            [ajax.core :refer [GET POST]]
            [maxwell.spy :as spy]))

(defn config [app-name server-url]
  {:session/id (random-uuid)
   :session/info (spy/all-info)
   :url server-url
   :app-name app-name})

;; ======================================================================
;; HTTP Helpers

(defn ->url [config path]
  (str (:url config) "/" path "/" (:app-name config)))

(defn post! [config path edn]
  (let [url (->url config path)]
    (POST url
          {:params (assoc edn
                          :session-id (:session/id config)
                          :timestamp (js/Date.))
           :format :edn
           :response-format :edn
           :handler (fn [_] (println "Ok " url))
           :error-handler (fn [e] (println "Request failed " url " " (pr-str e)))})))

;; ======================================================================
;; Session

(defn start-session! [app-name server-url]
  (let [config (config app-name server-url)]
    (POST (->url config "start")
          {:params {:session-id (:session/id config)
                    :timestamp (js/Date.)
                    :session/info (:session/info config)}})))

;; ======================================================================
;; Actions

(defn post-action! [config action]
  (assert (some? (:session/id config)) "The *session-id* was not initialized")
  (post! config "action" {:action (pr-str action)}))

(defn get-actions [config test-fn]
  (GET (->url config "actions")
       {:format :edn
        :response-format :edn
        :handler (fn [e]
                   (println "Action fetch")
                   (test-fn (mapv (partial mapv reader/read-string) e)))
        :error-handler (fn [e] (println "Recording failed: " e))}))

;; TODO: returning the action might lead to bad future api decisions
(defn log-action!
  "Returns the action as convenience"
  [config action]
  (post-action! config action)
  action)

;; ======================================================================
;; States

(defn get-state [config test-fn]
  (GET (->url config "state")
       {:format :edn
        :response-format :edn
        :handler (fn [e]
                   (println "State fetch")
                   (test-fn (mapv reader/read-string e)))
        :error-handler (fn [e] (println "Recording failed: " e))}))

(defn post-state! [config state]
  (post! config "state" {:state (pr-str state)}))

(defn log-states! [config ref]
  (add-watch ref ::states
             (fn [_ _ old-state new-state]
               (when-not (= old-state new-state)
                 (post-state! config new-state))))
  ref)

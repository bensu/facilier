(ns facilier.client
  "Helpers to log states from the client"
  (:require [cljs.reader :as reader]
            [ajax.core :refer [GET POST]]
            [maxwell.spy :as spy]
            [maxwell.kaos :as kaos]))

(defn config [server-url]
  {:session/id (random-uuid)
   :session/info (spy/all-info)
   :url server-url})

;; ======================================================================
;; HTTP Helpers

(defn ->url [config path]
  (println   (str (:url config) "/" path "/" (:session/id config)))
  (str (:url config) "/" path "/" (:session/id config)))

(defn post! [config path edn]
  (let [url (->url config path)]
    (POST url
          {:params (assoc edn
                          :session-id (:session/id config)
                          :session/status :ok
                          :browser/time (js/Date.))
           :format :edn
           :response-format :edn
           :handler (fn [_] (println "Ok " url))
           :error-handler (fn [e] (println "Request failed " url " " (pr-str e)))})))

;; ======================================================================
;; Session

(defn start-session! [server-url]
  (let [config (config server-url)]
    (post! config "session" (select-keys config [:session/info]))
    (kaos/watch-errors! :facilier/client
                        (fn [error]
                          (post! config "error" {:error (pr-str (dissoc error :error))}))
                        {:silence? false})
    config))



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
  (post-state! config @ref)
  (add-watch ref ::states
             (fn [_ _ old-state new-state]
               (when-not (= old-state new-state)
                 (post-state! config new-state))))
  ref)

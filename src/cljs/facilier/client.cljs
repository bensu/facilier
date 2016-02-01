(ns facilier.client
  "Helpers to log states from the client"
  (:require [cljs.reader :as reader]
            [ajax.core :refer [GET POST]]))

(def test-server-url "http://localhost:3005")

(def *session-id* (random-uuid))

;; ======================================================================
;; Actions

(defn post-action! [app-name action]
  (assert (some? *session-id*) "The *session-id* was not initialized")
  (POST (str test-server-url "/action/" app-name)
        {:params {:session-id *session-id*
                  :timestamp (js/Date.)
                  :action (pr-str action)}
         :format :edn
         :response-format :edn
         :handler (fn [_] (println "Action recorded"))
         :error-handler (fn [e] (println "Recording failed: " e))}))

(defn get-actions [app-name test-fn]
  (GET (str test-server-url "/actions/" app-name)
       {:format :edn
        :response-format :edn
        :handler (fn [e]
                   (println "Action fetch")
                   (test-fn (mapv (partial mapv reader/read-string) e)))
        :error-handler (fn [e] (println "Recording failed: " e))}))

(defn log-action! [app-name action]
  (post-action! app-name action)
  action)

;; ======================================================================
;; States

(defn get-state [app-name test-fn]
  (GET (str test-server-url "/state/" app-name)
       {:format :edn
        :response-format :edn
        :handler (fn [e]
                   (println "State fetch")
                   (test-fn (mapv reader/read-string e)))
        :error-handler (fn [e] (println "Recording failed: " e))}))

(defn post-state! [app-name state]
  (POST (str test-server-url "/state/" app-name)
        {:params {:state (pr-str state)}
         :format :edn
         :response-format :edn
         :handler (fn [_] (println "State recorded"))
         :error-handler (fn [e] (println "Recording failed: " e))}))

(defn log-states! [app-name ref]
  (add-watch ref ::states
             (fn [_ _ old-state new-state]
               (when-not (= old-state new-state)
                 (post-state! app-name new-state))))
  ref)

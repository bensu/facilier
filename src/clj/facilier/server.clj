(ns facilier.server
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [ring.middleware.params :as params]
            [ring.middleware.edn :refer [wrap-edn-params]]
            [ring.util.response :as response]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.adapter.jetty :as jetty]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.handler :as handler]))

;; ======================================================================
;; Helpers

(defn ok-response [body]
  {:status 200
   :headers {"Content-Type" "application/edn"}
   :body (pr-str body)})

(defn error-response [error]
  {:status 500
   :headers {"Content-Type" "application/edn"}
   :body (pr-str error)})

;; ======================================================================
;; Logic

(def root-dir "test/resources/predictive/states")

(defn session->file [session-id]
  (io/file root-dir (str session-id ".edn")))

(defn save-state! [session-id state]
  (let [f (session->file session-id)]
    (if (.exists f)
      (let [states (edn/read-string (slurp f))]
        (spit f (conj states state)))
      (spit f [state]))))

(defn get-state [session-id]
  (edn/read-string (slurp (session->file session-id))))

(defn delete-state! [session-id]
  (let [f (session->file session-id)]
    (when (.exists f)
      (.delete f))))

;; ======================================================================
;; HTTP Service

(defn handle-state! [params]
  (let [{:keys [session-id state]} params]
    (try
      (save-state! session-id state)
      (ok-response {:ok state})
      (catch Exception e
        (error-response e)))))

(defn return-state [params]
  (try
    (ok-response (get-state (:session-id params)))
    (catch Exception e
      (error-response e))))

(defn handle-delete! [{:keys [session-id]}]
  (try
    (delete-state! session-id)
    (ok-response {:ok "deleted"})
    (catch Exception e
      (error-response e))))

(defroutes app-routes
  (GET "/" [] (ok-response "<h1>YES</h1>"))
  (GET "/state/:session-id" {:keys [params]} (return-state params))
  (POST "/state/:session-id" {:keys [params]} (handle-state! params))
  (DELETE "/state/:session-id" {:keys [params]} (handle-delete! params)))

;; FIX: replace with wrap-cors
(defn add-cors [f]
  (fn [req]
    (update (f req)
            :headers
            #(merge % {"Access-Control-Allow-Origin" "*"
                       "Access-Control-Allow-Methods" "POST, GET, OPTIONS"
                       "Access-Control-Allow-Headers" "Content-Type"}))))

(def app-handler
  (-> app-routes
      wrap-edn-params
      add-cors
      #_(wrap-cors :access-control-allow-origin [#".*"]
                   :access-control-allow-methods [:get :put :post :delete])
      handler/site))

(defn start-jetty [handler port]
  (jetty/run-jetty handler {:port (Integer. port) :join? false}))

(defrecord Server [port jetty]
  component/Lifecycle
  (start [component]
    (println "Start server at port " port)
    (assoc component :jetty (start-jetty app-handler port)))
  (stop [component]
    (println "Stop server")
    (when jetty
      (.stop jetty))
    component))

(defn new-system [{:keys [port]}]
  (Server. (or port 3005) nil))

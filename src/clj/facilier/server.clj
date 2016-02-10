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

(defn handle [params f]
  (try
    (ok-response (f params))
    (catch Exception e
      (error-response e))))

(defn handle! [params f]
  (try
    (f params)
    (ok-response {:ok (:session-id params)})
    (catch Exception e
      (error-response e))))

;; ======================================================================
;; Session

(def root-dir "test/resources/sessions")

(defn session->file [session-id]
  (io/file root-dir (str session-id ".edn")))

(defn start-session! [session]
  (let [f (session->file (:session-id session))]
    (spit f session)))

(defn get-session [session-id]
  (let [f (session->file session-id)]
    (assert (.exists f))
    (edn/read-string (slurp f))))

(defn update-session! [session-id f]
  (let [file (session->file session-id)
        session (get-session session-id)]
    (spit file (f session))))

(defn delete-session! [session-id]
  (let [f (session->file session-id)]
    (when (.exists f)
      (.delete f))))

(defroutes session-routes
  (GET "/session/:id" [id] (handle id get-session))
  (POST "/session/:id" {:keys [params]} (handle! params start-session!))
  (DELETE "/session/:id" [id] (handle! id delete-session!)))

;; ======================================================================
;; States

(defn save-state! [{:keys [session-id state]}]
  (update-session! session-id
                   (fn [s]
                     (update s :states #(conj % state)))))

(defn get-states [session-id]
  (:states (get-session session-id)))

(defroutes state-routes
  (GET "/state/:session-id" [session-id] (handle session-id get-states))
  (POST "/state/:session-id" {:keys [params]} (handle! params save-state!)))

;; ======================================================================
;; Actions

(defn get-actions [session-id]
  (:actions (get-session session-id)))

(defn save-action! [{:keys [session-id action]}]
  (update-session! session-id
                   (fn [s] (update s :action #(conj % action)))))

(defroutes action-routes
  (GET "/action/:session-id" [session-id] (handle session-id get-actions))
  (POST "/action/:session-id" {:keys [params]} (handle! params save-action!)))

;; ======================================================================
;; Routes

(def all-routes
  (routes           state-routes session-routes

          action-routes
          (route/not-found "<h1>Page not found</h1>")))

;; ======================================================================
;; Middleware

(def app-handler
  (-> all-routes
      wrap-edn-params
      (wrap-cors :access-control-allow-origin [#"http://localhost:3000/*"]
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

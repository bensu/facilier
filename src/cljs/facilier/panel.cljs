(ns facilier.panel
  "Application to test and develop Facilier itself"
  (:import [goog.date DateTime])
  (:require [cljs.pprint :as pp :refer [pprint]]
            [cljs.reader :as reader]
            [clojure.string :as str]
            [om.core :as om]
            [sablono.core :as html :refer-macros [html]]
            [ajax.core :refer [GET]]
            [ankha.core :as ankha]
            [facilier.client :as f]))

(enable-console-print!)

;; ======================================================================
;; Data

(def info
  {:browser :chrome
   :browser-version "42.0.2311.135"
   :platform :mac
   :platform-version ""
   :engine :webkit
   :engine-version "537.36"
   :screen [1535 789]
   :agent "Mozilla/5.0 (X11; Linux x86_64) ..."})

(def test-url "http://localhost:3005")

(defonce config (f/start-session! test-url))

(defonce app-state
  (do
    (GET (str test-url "/session")
         {:format :edn
          :response-format :edn
          :handler (fn [{:keys [sessions]}]
                     (swap! app-state #(assoc % :sessions
                                              (zipmap (map :session/id sessions)
                                                      sessions))))})
    (f/log-states! config
                   (atom {:session nil
                          :sessions []}))))

;; ======================================================================
;; HTML

(defn browser-class [browser]
  {:pre [(keyword? browser)]}
  (str "fa icon-cell fa-" (name browser)))

(defn os-class [os]
  (str "fa icon-cell fa-" (if (= :mac os) "apple" (name os))))

(defn- big-name [k]
  {:pre [(keyword? k)]}
  (str/capitalize (name k)))

(defn full-platform-name [info]
  (let [{:keys [browser browser-version platform platform-version]} info]
    (str (big-name browser) " "browser-version
         " on " (big-name platform) " " platform-version)))

(defn status-class [status]
  {:pre [(keyword? status)]}
  (str "fa icon-cell fa-" (case status :ok "check" :error "ban")))

(defn platform-icons [info]
  [:span
   [:i {:class (os-class (:platform info))}]
   " "
   [:i {:class (browser-class (:browser info))}]])

(defn display-date [date]
  (.toLocaleString date))

(defn session-view [session owner {:keys [quit-fn]}]
  (reify
    om/IRender
    (render [_]
      (html
       (let [{:keys [session/id session/info session/status]} session
             date (:time/first session)]
         [:div.session nil
          [:h5 (str id " ")
           [:i {:class (status-class status)}]
           [:i.fa.fa-times.u-pull-right {:onClick (fn [_] (quit-fn id))}]]
          [:p (full-platform-name info)]
          [:p (display-date date)]
          #_[:p "Duration: " duration]
          [:div.state "State:" (om/build ankha/inspector
                                         (reader/read-string (last (:states session))))]])))))


;; ======================================================================
;; Table

(def title-row
  [:tr
   [:th.row-left "Session Id"] [:th "Time"] #_[:th "Duration"]
   [:th.center "Platform"] [:th.center.row-right "Status"]])

(defn display-uuid [uuid]
  (str (apply str (take 8 (str uuid))) "..."))

(defn row [session owner {:keys [click-fn]}]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [session/id session/status session/info]} session
            date (:time/first session)]
        (html
         [:tr.session-row {:onClick (fn [_]
                                      (click-fn id))}
          [:td.row-left (display-uuid id)]
          [:td (display-date date)]
          #_[:td.center duration]
          [:td.center (platform-icons info)]
          [:td.row-right.center [:i {:class (status-class status)}]]])))))

(defn widget [{:keys [sessions] :as data} owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div.container {}
        [:div.bar {}
         [:form {}
          [:label {:htmlFor "search"} "Search"]
          [:input#search {:type "search"}]]]
        (if-let [session-id (:session data)]
          (om/build session-view (get sessions session-id) {:opts {:quit-fn (fn [_]
                                                                              (om/update! data :session nil))}})
          (if (empty? (:sessions data))
            [:h5 "No sessions to show"]
            [:div.main {}
             [:table.session-table.u-full-width {}
              [:thead title-row]
              [:tbody (->> (vals (:sessions data))
                           (sort-by :time/first)
                           reverse
                           (mapv #(om/build row % {:opts {:click-fn (fn [uuid]
                                                                      (om/update! data :session uuid))}})))]]]))]))))


(defn init []
  (println "Start App")
  (om/root widget app-state {:target (. js/document (getElementById "container"))}))

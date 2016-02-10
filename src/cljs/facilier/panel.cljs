(ns facilier.panel
  "Application to test and develop Facilier itself"
  (:require [cljs.pprint :as pp :refer [pprint]]
            [clojure.string :as str]
            [om.core :as om]
            [sablono.core :as html :refer-macros [html]]
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

(def sessions
  (let [sessions (take 10 (repeatedly (fn [] {:uuid (str (random-uuid))
                                             :date (js/Date.)
                                             :duration "20 min"
                                             :info info
                                             :status :ok
                                             :state {:some "stuff"}})))]
    (zipmap (map :uuid sessions) sessions)))

(defonce app-state
  (f/log-states! "dev"
   (atom {:text "Something to say"
          :session nil
          :sessions sessions
          :toggle true})))

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

(defn session-view [session owner {:keys [quit-fn]}]
  (reify
    om/IRender
    (render [_]
      (html
       (let [{:keys [uuid date duration info status]} session]
         [:div.session nil
          [:i.fa.fa-times.u-pull-right {:onClick (fn [_] (quit-fn uuid))}]
          [:p "Session id: " uuid]
          [:p "Platform: " (full-platform-name info)]
          [:p "Time: " (str date)]
          [:p "Duration: " duration]
          [:p "Status: " [:i {:class (status-class status)}]]
          [:div.state "State:" (om/build ankha/inspector (:state session))]])))))


;; ======================================================================
;; Table

(def title-row
  [:tr
   [:th.row-left "Session Id"] [:th "Time"] [:th "Duration"]
   [:th "Platform"] [:th.row-right "Status"]])

(defn row [session owner {:keys [click-fn]}]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [uuid date duration info status]} session]
        (html
         [:tr.session-row {:onClick (fn [_]
                                      (click-fn uuid))}
          [:td.row-left uuid]
          [:td (str date)]
          [:td.center duration]
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
          [:div.main {}
           [:table.session-table.u-full-width {}
            [:thead title-row]
            [:tbody (mapv #(om/build row % {:opts {:click-fn (fn [uuid]
                                                               (om/update! data :session uuid))}})
                          (vals (:sessions data)))]]])]))))


(defn init []
  (println "Start App")
  (om/root widget app-state {:target (. js/document (getElementById "container"))}))

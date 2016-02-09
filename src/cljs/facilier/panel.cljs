(ns facilier.panel
  "Application to test and develop Facilier itself"
  (:require [om.core :as om]
            [sablono.core :as html :refer-macros [html]]
            [cljs.pprint :as pp :refer [pprint]]
            [ankha.core :as ankha]
            [facilier.client :as f]))

(enable-console-print!)

;; ======================================================================
;; Data

(def sessions
  (let [sessions (take 10 (repeatedly (fn [] {:uuid (str (random-uuid))
                                             :date (js/Date.)
                                             :duration "20 min"
                                             :platform "Chrome"
                                             :status "OK"
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

(defn session-view [session owner]
  (reify
    om/IRender
    (render [_]
      (html
       (let [{:keys [uuid date duration platform status]} session]
         [:div.session nil
          [:p "Session id: " uuid]
          [:p "Time: " (str date)]
          [:p "Duration: " duration]
          [:p "Status: " status]
          [:div.state "State:" (om/build ankha/inspector (:state session))]])))))


;; ======================================================================
;; Table

(def title-row
  [:tr [:th "Session Id"] [:th "Time"] [:th "Duration"] [:th "Platform"] [:th "Status"]])

(defn row [session owner {:keys [click-fn]}]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [uuid date duration platform status]} session]
        (html
         [:tr {:onClick (fn [_]
                        (click-fn uuid))}
          [:td uuid] [:td (str date)] [:td duration] [:td platform] [:td status]])))))

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
        [:div.main {}
         [:table.u-full-width {}
          [:thead title-row]
          [:tbody (mapv #(om/build row % {:opts {:click-fn (fn [uuid]
                                                             (om/update! data :session uuid))}})
                        (vals (:sessions data)))]]
         (when-let [session-id (:session data)]
           (om/build session-view (get sessions session-id)))]]))))


(defn init []
  (println "Start App")
  (om/root widget app-state {:target (. js/document (getElementById "container"))}))

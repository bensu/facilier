(ns facilier.panel
  "Application to test and develop Facilier itself"
  (:import [goog.date DateTime])
  (:require [cljs.pprint :as pp]
            [cljs.reader :as reader]
            [clojure.string :as str]
            [facilier.test :as ft :refer-macros [defn! defmethod! handle]]
            [om.core :as om]
            [sablono.core :as html :refer-macros [html]]
            [ajax.core :refer [GET]]
            [facilier.client :as f]))

(enable-console-print!)

;; ======================================================================
;; Data

(defonce app-state
  (atom {:session/current nil
         :app/name nil
         :session/all {}}))

(def test-url "http://localhost:3005")

(defn! http-request! [url cb]
  (GET url
       {:format :edn
        :response-format :edn
        :handler cb}))

(defmulti request! (fn [k params cb] k))

(defn by-id [ss]
  (-> (map :session/id ss)
      (zipmap ss)
      (dissoc (str (:session/id f/*config*)))))

(defmethod request! :session/all
  [_ _ cb]
  (http-request!
   (str test-url "/session")
   (handle [e]
     (let [{:keys [sessions]} e]
       (cb {:session/all (->> sessions
                              (group-by :app/name)
                              (map (fn [[n ss]] [n (by-id ss)]))
                              (into {}))})))))

(defmethod request! :session/full
  [_ {:keys [session/id]} cb]
  (when (some? id)
    (http-request! (str test-url "/session/" id)
                   (handle [e]
                     (let [{:keys [session]} e]
                       (cb {:session/full session}))))))

(defmethod request! :session/list
  [_ _ cb]
  (request! :session/all _ cb))

(defmulti step (fn [_ [k _]] k))

(defmethod step :session/select
  [state [_ {:keys [session/id]}]]
  (assoc state :session/current id))

(defmethod step :session/close
  [state _]
  (assoc state :session/current nil))

(defmethod step :session/load
  [state [_ {:keys [session/all]}]]
  (-> state
      (assoc :app/name (first (keys all)))
      (update :session/all #(merge % all))))

(defmethod step :session/load-one
  [state [_ session]]
  (update-in state
             [:session/all (:app/name session)]
             #(assoc % (:session/id session) session)))

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
  (str "fa fa-circle " (case status :status/ok "green" :status/error "red")))

(defn platform-icons [info]
  [:span
   [:i {:class (os-class (:platform info))}]
   " "
   [:i {:class (browser-class (:browser info))}]])

(defn display-date [date]
  (.toLocaleString date))

(defn ->code [edn]
  [:pre [:code (with-out-str (pp/pprint edn))]])

(defn session-view [session owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (when-not (contains? session :state)
        (request! :session/full session
                  (fn [{:keys [session/full]}]
                    (f/raise! [:session/load-one full])))))
    om/IRender
    (render [_]
      (html
       (let [{:keys [session/id session/info]} session
             date (:time/first session)]
         [:div.session nil
          [:h5.session-title (str id " ")
           [:i {:class (status-class (:session/status session))}]
           [:i.fa.fa-times.u-pull-right {:onClick (handle [e]
                                                          (f/raise! [:session/close nil]))}]]
          [:p "Version Commit: " (:app/commit session)]
          [:p (full-platform-name info)]
          [:p (display-date date)]
          #_[:p "Duration: " duration]
          (when-let [error (last (:errors session))]
            [:div.state "Error:" (->code  (reader/read-string error))])
          (when-not (empty? (:actions session))
            [:div.state "Actions: "
             (->code (mapv reader/read-string (:actions session)))])
          (when-not (empty? (:events session))
            [:div.state "Events: "
             (->code (mapv (comp ft/read-event reader/read-string)
                           (:events session)))])
          (when-let [state (last (:states session))]
            ;; (if state?)
            [:div.state  "State:" (->code (reader/read-string state))]
            ;; [:div.state {:onClick (fn [_]
            ;;                         (om/update-state! this update :state? not))}
            ;;  [:i.fa.fa-chevron-right] "State"]
            )])))))

;; ======================================================================
;; Table

(def title-row
  [:tr
   [:th.row-left "Version"]
   [:th "Session Id"] [:th "Time"] #_[:th "Duration"]
   [:th.center "Platform"] [:th.center.row-right "Status"]])

(defn display-uuid [uuid]
  (str (apply str (take 8 (str uuid))) "..."))

(defn row [session owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [session/id session/info app/commit session/status]} session
            date (:time/first session)]
        (html
         [:tr.session-row
          {:onClick (handle [e]
                            (f/raise! [:session/select {:session/id id}]))}
          [:td.row-left (display-uuid commit)]
          [:td.row-left (display-uuid id)]
          [:td (display-date date)]
          #_[:td.center duration]
          [:td.center (platform-icons info)]
          [:td.row-right.center [:i {:class (status-class status)}]]])))))

(defn table [sessions owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div.main {}
        [:table.session-table.u-full-width {}
         [:thead title-row]
         [:tbody (mapv #(om/build row % {:key :session/id}) sessions)]]]))))

(defn widget [data owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (when (empty? (:session/all data))
        (request! :session/all nil
                 (fn [d]
                   (f/raise! [:session/load d])))))
    om/IRender
    (render [this]
      (let [{:keys [session/current session/all]} data]
        (html
         [:div.container {}
          [:h2.main-title (:app/name data)]
          #_[:div.bar {}
             [:form {}
              [:label {:htmlFor "search"} "Search"]
              [:input#search {:type "search"}]]]
          (cond
            (some? current)
            (om/build session-view (get-in (:session/all data)
                                           [(:app/name data) current]))

            (empty? all)
            [:h5.empty "No sessions to show"]

            :else
            (om/build table (->> (vals (get all (:app/name data)))
                                 (sort-by :time/first)
                                 reverse
                                 vec)))])))))

(defn init []
  (println "Start App")
  (f/monitor! widget
              {:model app-state
               :step step
               :target (. js/document (getElementById "container"))}
              {:app/name "facilier-panel"
               :test/url test-url
               :log-state? true}))

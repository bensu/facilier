(ns facilier.panel
  "Application to test and develop Facilier itself"
  (:import [goog.date DateTime])
  (:require [cljs.pprint :as pp]
            [cljs.reader :as reader]
            [clojure.string :as str]
            [om.next :as om :refer-macros [defui]]
            [sablono.core :as html :refer-macros [html]]
            [ajax.core :refer [GET]]
            [facilier.client :as f]))

(enable-console-print!)

;; ======================================================================
;; Data

(defmulti read om/dispatch)

(defmethod read :session
  [{:keys [state ast] :as env} k {:keys [query]}]
  (let [v (get @state k {})]
    (merge {:value v}
           (when (or (not (contains? v :states))
                     (not (contains? v :actions))
                     (not (contains? v :errors)))
             {:server ast}))))

(defmethod read :session/list
  [{:keys [state ast]} k {:keys [n]}]
  (let [v (get @state k)]
    (merge {:value (->> (take n (vals v))
                        (sort-by :time/first)
                        reverse
                        vec)}
           (when (nil? v)
             {:server ast}))))

(defmethod read :default
  [{:keys [state] :as env} key params]
  (let [st @state]
    (if-let [[_ v] (find st key)]
      {:value v}
      {:value :not-found})))

(defonce app-state
  (atom {:session/current nil
         :session/list nil}))

(def test-url "http://localhost:3005")

(defonce facilier-config (f/start-session! test-url app-state {:log-state? true}))

(defmulti request (fn [k params cb] k))

(defmethod request :session/list
  [_ _ cb]
  (GET (str test-url "/session")
       {:format :edn
        :response-format :edn
        :handler (fn [{:keys [sessions]}]
                   (cb {:session/list (zipmap (map :session/id sessions)
                                              sessions)}))}))

(defmethod request :session
  [_ {:keys [session/id]} cb]
  (GET (str test-url "/session/" id)
       {:format :edn
        :response-format :edn
        :handler cb}))

(defn send [{:keys [server]} cb]
  (when server
    (let [k (get-in (om/query->ast server) [:children 0 :key])]
      (request k {} cb))))

(defmulti mutate (fn [_ key _] key))

(defmethod mutate `session/select
  [{:keys [state]} _ {:keys [id]}]
  {:value {:keys [:session]}
   :action #(swap! state assoc :session id)})

(defmethod mutate `session/close
  [{:keys [state]} _ _]
  {:value {:keys [:session]}
   :action #(swap! state assoc :session nil)})

(defmethod mutate `sessions/load
  [{:keys [state]} _ {:keys [sessions]}]
  {:value {:keys [sessions]}
   :action #(swap! state assoc :sessions (zipmap (map :session/id sessions)
                                                 sessions))})


(def reconciler
  (let [r (om/reconciler {:state app-state
                          :parser (om/parser {:read read :mutate mutate})
                          :send send
                          :remotes [:remote :server]})]
    (specify! r
      om/ITxIntercept
      (tx-intercept [this tx]
        (f/post-action! facilier-config tx)
        (om/transact! this tx)))))

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

(defui Session
  static om/IQuery
  (query [_] '[:session])
  Object
  (initLocalState [_]
                  {:state? false
                   :error? false})
  (render [this]
          (html
           (let [{:keys [session/id session/info] :as session} (om/props this)
                 date (:time/first session)
                 {:keys [state? error?]} (om/get-state this)]
             [:div.session nil
              [:h5 (str id " ")
               [:i {:class (status-class (:session/status session))}]
               [:i.fa.fa-times.u-pull-right {:onClick (fn [_]
                                                        (om/transact! this `[(session/close)]))}]]
              [:p "Version Commit: " (:git/commit session)]
              [:p (full-platform-name info)]
              [:p (display-date date)]
              #_[:p "Duration: " duration]
              (when-let [error (last (:errors session))]
                [:div.state "Error:" (->code  (reader/read-string error))])
              (when-not (empty? (:actions session))
                [:div.state "Actions: "
                 (->code (mapv reader/read-string (:actions session)))])
              (when-let [state (last (:states session))]
                ;; (if state?)
                [:div.state  "State:" (->code (reader/read-string state))]
                ;; [:div.state {:onClick (fn [_]
                ;;                         (om/update-state! this update :state? not))}
                ;;  [:i.fa.fa-chevron-right] "State"]
                )]))))

(def session-view (om/factory Session))

;; ======================================================================
;; Table

(def title-row
  [:tr
   [:th.row-left "Version"]
   [:th "Session Id"] [:th "Time"] #_[:th "Duration"]
   [:th.center "Platform"] [:th.center.row-right "Status"]])

(defn display-uuid [uuid]
  (str (apply str (take 8 (str uuid))) "..."))

(defui Row
  static om/IQuery
  (query [_] '[:session/id :session/info :git/commit :time/first :session/status])
  Object
  (render [this]
          (let [session (om/props this)
                {:keys [session/id session/info git/commit session/status]} session
                date (:time/first session)]
            (html
             [:tr.session-row {:onClick (fn [_]
                                          (om/transact! this `[(session/select {:id ~id})]))}
              [:td.row-left (display-uuid commit)]
              [:td.row-left (display-uuid id)]
              [:td (display-date date)]
              #_[:td.center duration]
              [:td.center (platform-icons info)]
              [:td.row-right.center [:i {:class (status-class status)}]]]))))

(def row (om/factory Row))

(defui Widget
  static om/IQueryParams
  (params [_] {:n 20})
  static om/IQuery
  (query [_] '[:session/current (:session/list {:n ?n})])
  Object
  (render [this]
          (let [{:keys [session/current session/list]} (om/props this)]
            (html
             [:div.container {}
              [:div.bar {}
               [:form {}
                [:label {:htmlFor "search"} "Search"]
                [:input#search {:type "search"}]]]
              (if-let [session-id current]
                (session-view (get list session-id))
                (if (empty? list)
                  [:h5 "No sessions to show"]
                  [:div.main {}
                   [:table.session-table.u-full-width {}
                    [:thead title-row]
                    [:tbody (mapv row list)]]]))]))))

(defn init []
  (println "Start App")
  (om/add-root! reconciler Widget (. js/document (getElementById "container"))))

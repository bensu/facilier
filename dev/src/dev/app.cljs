(ns dev.app
  "Application to test and develop Facilier itself"
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [facilier.client :as f]))

(defonce app-state
  (f/log-states! "dev"
   (atom {:text "Something to say"
          :toggle true})))

(defn read
  [{:keys [state] :as env} key params]
  (if-let [[_ value] (find @state key)]
    {:value value}
    {:value nil}))

(defmulti mutate om/dispatch)

(defmethod mutate :default [_ _ _] {:value []})

(defmethod mutate `todo/toggle
  [{:keys [state]} _ _]
  {:value [:toggle]
   :action (fn []
             (swap! app-state #(update % :toggle not)))})

(defn cast! [this action]
  (f/log-action! "dev" action)
  (om/transact! this action))

(def parser (om/parser {:read read :mutate mutate}))

(def reconciler
  (om/reconciler {:state app-state
                  :parser parser}))

(defui Widget
  static om/IQuery
  (query [_] [:text :toggle])
  Object
  (render [this]
          (let [{:keys [text toggle]} (om/props this)]
            (dom/div nil
                     (dom/div nil text)
                     (dom/span nil
                               (dom/input #js {:type "checkbox"
                                               :id "toggle"
                                               :checked toggle
                                               :onClick (fn [_]
                                                          (cast! this `[(todo/toggle nil)]))})
                               (dom/label #js {:htmlFor "toggle"} "Toggle"))))))

(defn init []
  (println "Start App")
  (om/add-root! reconciler Widget (. js/document (getElementById "container"))))

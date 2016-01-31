(ns dev.app
  "Application to test and develop Facilier itself"
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]))

(defonce app-state (atom {:text "Something to say"}))

(defui Widget
  static om/IQuery
  (query [_] [:text])
  Object
  (render [this]
          (let [{:keys [text]} (om/props this)]
            (dom/div nil text))))

(defn read
  [{:keys [state] :as env} key params]
  (if-let [[_ value] (find @state key)]
    {:value value}
    {:value nil}))

(defmulti mutate om/dispatch)

(defmethod mutate :default [_ _ _] {:value []})

(def parser (om/parser {:read read :mutate mutate}))

(def reconciler
  (om/reconciler {:state app-state
                  :parser parser}))

(defn init []
  (println "Start App")
  (om/add-root! reconciler Widget (. js/document (getElementById "container"))))

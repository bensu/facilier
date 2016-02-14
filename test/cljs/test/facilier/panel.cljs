(ns test.facilier.panel
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures async]]
            [cljs-react-test.simulate :as sim]
            [cljs-react-test.utils :as tu]
            [facilier.test :as ft :refer-macros [defn! defstateprop defsessionprop]]
            [dommy.core :as dommy :refer-macros [sel1 sel]]
            [om.core :as om]
            [facilier.client :as f]
            [facilier.panel :as p]))

(def config {:url "http://localhost:3005"})

(defstateprop toggle-on config [state]
  (let [c (tu/new-container!)
        rt (om/root p/widget (atom state) {:target c})
        _ (om/render-all)
        {:keys [session/all session/current]} state]
    (when (empty? all)
      (let [row-ele (tu/find-by-tag rt "h5")]
        (is (re-find #"empty" (.-className row-ele)))))
    (when-not (empty? all)
      (when (nil? current)
        (is (count all) (count (tu/find-by-tag rt "td"))))
      (when (some? current)
        (let [n (tu/find-one-by-class rt "session-title")]
          (is (= current (.-innerText n))))))
    (tu/unmount! c)))

(defsessionprop elses config [session]
  (when-not (empty? (:actions session))
    (let [c (tu/new-container!)
          app-state (atom (:state/init session))
          rt (om/root p/widget app-state {:target c})
          _ (om/render-all)]
      (doseq [action (:actions session)]
        (p/raise! action)
        (om/render-all rt)
        (let [state @app-state
              {:keys [session/all session/current]} state]
          (let [[t l] action]
            (when (= :session/close t)
              (is (nil? current)))
            (when (= :session/select t)
              (is (= current (:session/id l)))))
          (when (empty? all)
            (let [row-ele (sel1 c [:h5])]
              (is (re-find #"empty" (.-className row-ele)))))
          (when-not (empty? all)
            (when (nil? current)
              (is (count all) (count (sel c [:td]))))
            (when (some? current)
              (let [n (sel1 c :.session-title)]
                (is (= current (.-innerText n)))))))
        (tu/unmount! c)))))

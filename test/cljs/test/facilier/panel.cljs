(ns test.facilier.panel
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures async]]
            [cljs-react-test.simulate :as sim]
            [cljs-react-test.utils :as tu]
            [facilier.test :as ft :refer-macros [defn! defstateprop]]
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

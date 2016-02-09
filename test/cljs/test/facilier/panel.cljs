(ns test.facilier.panel
  (:require-macros [facilier.test :as ftest :refer [def-state-inv]])
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures async]]
            [cljs-react-test.simulate :as sim]
            [cljs-react-test.utils :as tu]
            [dommy.core :as dommy :refer-macros [sel1 sel]]
            [om.next :as om :refer-macros [defui]]
            [facilier.client :as f]
            [facilier.panel :as app]))


(def-state-inv toggle-on "dev" [state]
  (let [c (tu/new-container!)
        reconciler (om/reconciler {:state (atom state)
                                   :parser app/parser})
        _ (om/add-root! reconciler app/Widget c)
        toggle-ele (sel1 c [:input])]
    (is (= (.-checked toggle-ele) (:toggle state)))
    (tu/unmount! c)))

;; FIX: this is need for some boot-cljs-test misconfiguration
(cljs.test/run-tests)

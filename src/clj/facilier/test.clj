(ns facilier.test
  (:require [cljs.test]))

(defmacro def-state-inv [test-name app-name binding & body]
  (assert (vector? binding) "A vector for bindings")
  (assert (= 1 (count binding)) "One element in the binding")
  (let [state-sym (first binding)]
    `(cljs.test/deftest ~test-name
       (cljs.test/async done#
                        (facilier.client/get-state ~app-name
                                                   (fn [states#]
                                                     (doseq [~state-sym states#]
                                                       ~@body)
                                                     (done#)))))))

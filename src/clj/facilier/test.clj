(ns facilier.test
  (:require [cljs.test]))

(defmacro defsessionprop [test-name config binding & body]
  (assert (vector? binding) "A vector for bindings")
  (assert (= 1 (count binding)) "One element in the binding")
  (let [state-sym (first binding)]
    `(cljs.test/deftest ~test-name
       (cljs.test/async done#
                        (facilier.client/get-session ~config
                                                     (fn [states#]
                                                       (facilier.test/start-testing!)
                                                       (doseq [~state-sym states#]
                                                         ~@body)
                                                       (facilier.test/stop-testing!)
                                                       (done#)))))))

(defmacro defstateprop [test-name config binding & body]
  (assert (vector? binding) "A vector for bindings")
  (assert (= 1 (count binding)) "One element in the binding")
  (let [state-sym (first binding)]
    `(cljs.test/deftest ~test-name
       (facilier.test/start-testing!)
       (cljs.test/async done#
                        (facilier.client/get-state ~config
                                                   (fn [states#]
                                                     (doseq [~state-sym states#]
                                                       ~@body)
                                                     (facilier.test/stop-testing!)
                                                     (done#)))))))

(defmacro defn! [name docs-bindings & decls]
  (assert (symbol name))
  (assert (or (string? docs-bindings)
              (vector? docs-bindings)))
  (when (string? docs-bindings)
    (assert (vector? (first decls))))
  (if (string? docs-bindings)
    `(defn ~name
       ~docs-bindings
       ~(first decls)
       (when-not facilier.test/test?
         ~@(rest decls)))
    `(defn ~name ~docs-bindings
       (when-not facilier.test/test?
         ~@decls))))

(defmacro defmethod! [name val bindings & body]
  `(defmethod ~name ~val ~bindings
     (when-not facilier.test/test?
       ~@body)))

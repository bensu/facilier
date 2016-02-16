(ns facilier.test
  (:require [cljs.test]))

(defmacro defstateprop [test-name config binding & body]
  (assert (vector? binding) "A vector for bindings")
  (assert (= 1 (count binding)) "One element in the binding")
  (let [state-sym (first binding)]
    `(cljs.test/deftest ~test-name
       (cljs.test/async
        done#
        (facilier.client/get-state ~config
                                   (fn [states#]
                                     (facilier.test/start-testing!)
                                     (doseq [~state-sym states#]
                                       ~@body)
                                     (facilier.test/stop-testing!)
                                     (done#))
                                   (fn [a#] (done#)))))))

(defmacro defsessionprop [test-name config binding & body]
  (assert (vector? binding) "A vector for bindings")
  (assert (= 1 (count binding)) "One element in the binding")
  (let [state-sym (first binding)]
    `(cljs.test/deftest ~test-name
       (cljs.test/async
        done#
        (facilier.client/get-sessions ~config
                                      (fn [states#]
                                        (facilier.test/start-testing!)
                                        (doseq [~state-sym states#]
                                          ~@body)
                                        (facilier.test/stop-testing!)
                                        (done#))
                                      (fn [a#] (done#)))))))

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

(defmacro handle [bindings & body]
  "Creates and registers an event handler"
  {:style/indent 1}
  (assert (and (vector? bindings) (= 1 (count bindings)))
          "Bindings should be a one element vector")
  ;; FIX: doesn't work with destructuring!
  (let [e-sym (first bindings)]
    `(let [id# (facilier.test/next-id!)
           f# (fn ~bindings
                (facilier.client/log-event! id# ~e-sym)
                ~@body)]
       (facilier.test/add-handler! id# f#)
       f#)))

(defmacro defhandler [name bindings & body]
  `(def ~name
     (handle ~bindings ~@body)))

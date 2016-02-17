(ns facilier.client
  "Helpers to log states from the client"
  (:require-macros [facilier.helper :as helper])
  (:require [cljs.reader :as reader]
            [clojure.string :as str]
            [om.core :as om]
            [sablono.core :as html :refer-macros [html]]
            [facilier.test :as t :refer-macros [defn!]]
            [util.obj :as u]
            [ajax.core :refer [GET POST]]
            [maxwell.spy :as spy]
            [maxwell.kaos :as kaos]))

;; ======================================================================
;; Config

(def ^:dynamic *config*)

(defn config [app-name server-url]
  {:session/id (random-uuid)
   :session/info (spy/all-info)
   :app/name app-name
   :app/commit (helper/git-commit)
   :test/url server-url})

;; ======================================================================
;; History

(defonce history (atom {:debugger? false
                        :states []}))

;; ======================================================================
;; HTTP Helpers

(defn ->url [config path]
  (str (:test/url config) "/" path "/" (:session/id config)))

(defn! post! [config path edn]
  (let [url (->url config path)]
    (POST url
          {:params (assoc edn
                          :session-id (:session/id config)
                          :browser/time (js/Date.))
           :format :edn
           :response-format :edn
           :handler (fn [_] (println "Ok" url))
           :error-handler (fn [e] (println "Request failed " url " " (pr-str e)))})))

;; ======================================================================
;; Actions

(defn post-action! [config action]
  (post! config "action" {:action (pr-str action)}))

(defn read-ents [es]
  (mapv (fn [s] (if (string? s) (reader/read-string s) s)) es))

(defn! get-actions [config test-fn]
  (GET (->url config "actions")
       {:format :edn
        :response-format :edn
        :handler (fn [states]
                   (println "Action fetch")
                   (test-fn (read-ents states)))
        :error-handler (fn [e] (println "Recording failed: " e))}))

;; TODO: returning the action might lead to bad future api decisions
(defn log-action!
  "Returns the action as convenience"
  [config action]
  (when-not (:debugger? @history)
    (post-action! config action))
  action)

;; ======================================================================
;; Events

(defn post-event! [config event]
  (post! config "event" {:event (pr-str event)}))

(defn log-event! [id event]
  (when-not (:debugger? @history)
    (post-event! *config* (merge {:handler/id id
                                  :timestamp (js/Date.)}
                                 (if (t/edn? event)
                                   {:event/edn (pr-str event)}
                                   {:event/json (u/serialize event)})))))

;; ======================================================================
;; States

(defn! get-state [config cb ecb]
  (GET (->url config "state")
       {:format :edn
        :response-format :edn
        :handler (fn [e]
                   (println "State fetch")
                   (cb (mapv (fn [s]
                               (if (string? s) (reader/read-string s) s))
                             e)))
        :error-handler (fn [e]
                         (println "recording failed: " e)
                         (ecb e))}))



(defn post-state! [config state]
  (post! config "state" {:state (pr-str state)}))

(defn log-states! [config ref]
  (add-watch ref ::states
             (fn [_ _ old-state new-state]
               (when-not (= old-state new-state)
                 (when-not (:debugger? @history)
                   (swap! history #(update % :states (fn [s] (conj s new-state))))
                   (post-state! config new-state)))))
  ref)

;; ======================================================================
;; Session

(defn start-session! [ref {:keys [app/name test/url log-state?]}]
  (let [config (config name url)]
    (set! *config* config)
    (post! config "session" (-> config
                                (select-keys [:app/name :app/commit :session/info])
                                (assoc :state/init @ref)))
    (when log-state?
      (log-states! config ref))
    (kaos/watch-errors! :facilier/client
                        (fn [error]
                          (post! config "error" {:error (pr-str (dissoc error :error))}))
                        {:silence? false})
    config))

(defn! get-sessions [config cb ecb]
  (GET (str (:test/url config) "/full-sessions/10")
       {:format :edn
        :response-format :edn
        :handler (fn [sessions]
                   (println "Session fetch")
                   (cb (->> sessions
                            (map #(update % :states read-ents))
                            (mapv #(update % :actions read-ents))
                            (mapv #(update % :events read-ents)))))
        :error-handler (fn [e]
                         (println "Session fetch failed: " e)
                         (ecb e))}))

;; ======================================================================
;; Om API

(defn radio-item [current-val val f]
  [:li {:onClick (fn [_] (f val))}
   [:input {:name "source" :type "radio" :value (name val)
            :checked (= val current-val)}]
   (str/capitalize (name val))])

(defn select-button
  [{:keys [current val]} owner {:keys [click-fn]}]
  (reify
    om/IRender
    (render [_]
      (html
       [:button {:className (if (= current val)
                              "source button pressed"
                              "source button")
                 :onClick (fn [_] (click-fn val))}
        (str/capitalize (name val))]))))

(defn debugger [data owner opts]
  (reify
    om/IInitState
    (init-state [_] {:source :states
                     :idx (dec (count (:states @history)))})
    om/IWillMount
    (will-mount [_]
      (set! facilier.test/test? false)
      (swap! history (fn [h] (assoc h :debugger? true))))
    om/IWillUnmount
    (will-unmount [_]
      (set! facilier.test/test? false)
      (swap! history (fn [h] (assoc h :debugger? false))))
    om/IRenderState
    (render-state [_ {:keys [source idx]}]
      (html
       [:div.debugger.eleven.columns
        [:div.row
         (letfn [(change [v]
                   (om/set-state! owner :source v))]
           [:ul.source.five.columns
            (om/build select-button {:current source
                                     :val :states}
                      {:opts {:click-fn change}})
            (om/build select-button {:current source
                                     :val :actions}
                      {:opts {:click-fn change}})
            (om/build select-button {:current source
                                     :val :events}
                      {:opts {:click-fn change}})])
         [:div.scroller.six.columns
          [:input {:type "range"
                   :min 0
                   :max (dec (count (:states @history)))
                   :step 1
                   :value idx
                   :onChange (fn [e]
                               (let [v (int (.. e -target -value))]
                                 (println v)
                                 (om/update! data (get-in @history [:states v]))
                                 (om/set-state! owner :idx v)))}]]]]))))

(def ^:dynamic raise!)

(defn monitor-component [data owner {:keys [c step config]}]
  (reify
    om/IInitState
    (init-state [_] {:debugger? false})
    om/IWillMount
    (will-mount [_]
      (set! raise! (fn [action]
                     (log-action! *config* action)
                     (om/transact! data #(step % action)))))
    om/IRenderState
    (render-state [_ {:keys [debugger?]}]
      (html
       [:div
        (om/build c data)
        [:footer
         (if debugger?
           [:div.debugger-container.row
            (om/build debugger data)
            [:div.one.column
             [:i.fa.fa-times.close-debugger
              {:onClick (fn [_]
                          (om/set-state! owner :debugger? false))}]]]
           [:div.left
            [:i.footer-icon.fa.fa-question
             {:onClick (fn [_]
                         (om/set-state! owner :debugger? true))}]])]]))))


(defn monitor! [component {:keys [model step target]} config]
  (start-session! model config)
  (om/root monitor-component model
           {:opts {:c component :config config :step step}
            :target target}))

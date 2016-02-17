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

(defn read-session [s]
  (-> s
      (update :states read-ents)
      (update :actions read-ents)
      (update :events read-ents)))

(defn! get-sessions [config cb ecb]
  (GET (str (:test/url config) "/full-sessions/10")
       {:format :edn
        :response-format :edn
        :handler (fn [sessions]
                   (println "Session fetch")
                   (cb (mapv read-session sessions)))
        :error-handler (fn [e]
                         (println "Session fetch failed: " e)
                         (ecb e))}))

(defn get-session [id config cb ecb]
  (GET (str (:test/url config) "/session/" id)
       {:format :edn
        :response-format :edn
        :handler (fn [{:keys [session]}]
                   (cb (read-session session)))
        :error-handler (fn [e]
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
      (om/update! data (last (:states @history)))
      (swap! history (fn [h] (assoc h
                                   :debugger? false
                                   :idx (dec (count (:states h)))))))
    om/IRenderState
    (render-state [_ {:keys [source idx]}]
      (html
       [:div.debugger.ten.columns
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
         [:div.scroller.seven.columns
          [:input {:type "range"
                   :min 0
                   :max (dec (count (:states @history)))
                   :step 1
                   :value idx
                   :onChange (fn [e]
                               (let [v (int (.. e -target -value))]
                                 (om/update! data (get-in @history [:states v]))
                                 (om/set-state! owner :idx v)))}]]]]))))

(def ^:dynamic raise!)

(defn new-history! [data owner session]
  (let [states (:states session)
        new-state (last states)]
    (println (type new-state))
    (om/set-state! owner :idx (dec (count states)))
    (om/set-state! owner :new-session? false)
    (om/transact! data (fn [d] new-state))
    (swap! history (fn [h] (assoc h :states states)))))

(defn session-input
  [{:keys [value error?]} owner {:keys [change-fn enter-fn]}]
  (reify
    om/IDidMount
    (did-mount [_]
      (.focus (om/get-node owner "input")))
    om/IRender
    (render [_]
      (html
       [:div.ten.columns
        [:input {:ref "input"
                 :className (if error?
                              "session-input error"
                              "session-input")
                 :type "text" :value value
                 :onChange (fn [e]
                             (let [v (.. e -target -value)]
                               (change-fn v)))
                 :onKeyDown (fn [e]
                              (when (= 13 (.-keyCode e))
                                (enter-fn value)))}]]))))

(defn monitor-component
  [data owner {:keys [c step config]}]
  (reify
    om/IInitState
    (init-state [_] {:debugger? false
                     :new-session? false
                     :error? false
                     :value ""})
    om/IWillMount
    (will-mount [_]
      (set! raise! (fn [action]
                     (log-action! *config* action)
                     (om/transact! data #(step % action)))))
    om/IRenderState
    (render-state [_ {:keys [debugger? new-session? value error?]}]
      (html
       [:div
        (om/build c data)
        [:footer
         (if debugger?
           [:div.debugger-container.row
            [:div.one.column
             (if new-session?
               [:i.new-session.fa.fa-arrow-left
                {:onClick (fn [_]
                            (om/set-state! owner :new-session? false))}]
               [:i.new-session.fa.fa-folder-open
                {:onClick (fn [_]
                            (om/set-state! owner :new-session? true))}])]
            (if new-session?
              (om/build session-input {:value value :error? error?}
                        {:opts {:change-fn (fn [v]
                                             (om/set-state! owner :error? false)
                                             (om/set-state! owner :value v))
                                :enter-fn (fn [v]
                                            (get-session v *config*
                                                         (fn [s]
                                                           (new-history! data owner s))
                                                         (fn [_]
                                                           (om/set-state! owner :error? true))))}})
              (om/build debugger data))
            [:div.one.column
             [:i.fa.fa-times.close-debugger
              {:onClick (fn [_]
                          (om/set-state! owner :debugger? false))}]]]
           [:div.left
            [:i.footer-icon.fa.fa-step-backward
             {:onClick (fn [_]
                         (om/set-state! owner :debugger? true))}]])]]))))

(defn monitor! [component {:keys [model step target]} config]
  (start-session! model config)
  (om/root monitor-component model
           {:opts {:c component :config config :step step}
            :target target}))

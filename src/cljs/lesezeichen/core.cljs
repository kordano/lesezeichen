(ns lesezeichen.core
  (:require [figwheel.client :as figw :include-macros true]
            [weasel.repl :as ws-repl]
            [cljs.core.async :refer [put! chan <! >! alts! timeout close!] :as async]
            [cljs.reader :refer [read-string] :as read]
            [kioo.om :refer [content set-attr do-> substitute listen]]
            [kioo.core :refer [handle-wrapper]]
            [om.core :as om :include-macros true]
            [om.dom :as omdom]
            [lesezeichen.com :refer [connect!]])
  (:require-macros [kioo.om :refer [defsnippet deftemplate]]
                   [cljs.core.async.macros :refer [go go-loop]]))


(enable-console-print!)

(println "ALL HAIL TO MASTER KONNY!")

(def uri (goog.Uri. js/location.href))

(def ssl? (= (.getScheme uri) "https"))

(def app-state (atom {:bookmarks []}))


;; fire up repl
#_(do
    (ns weasel.startup)
    (require 'weasel.repl.websocket)
    (cemerick.piggieback/cljs-repl
        :repl-env (weasel.repl.websocket/repl-env
                   :ip "0.0.0.0" :port 9001)))


;; weasel websocket
(if (= "localhost" (.getDomain uri))
  (do
    #_(ws-repl/connect "ws://localhost:9001" :verbose true)
    (figw/watch-and-reload
     ;; :websocket-url "ws://localhost:3449/figwheel-ws" default
     :jsload-callback (fn [] (print "reloaded")))))



;; --- DATABASE ---

(defn get-bookmarks [stage]
  (when stage nil))


(defn add-bookmark [owner]
  (let [url (om/get-state owner :url-input-text)
        ws-in (om/get-state owner :ws-in)]
    (go (>! ws-in {:topic :add-bookmark :data {:email "eve@topiq.es" :url url}}))))



;; --- MAIN VIEW ---

(defn handle-text-change [e owner text]
  (om/set-state! owner text (.. e -target -value)))


(defsnippet url "templates/bookmarks.html" [:.list-group-item]
  [{:keys [title url ts]}]
  {[:.url-text] (do-> (set-attr :href url)
                      (content (if (= "" title) url title)))
   [:.url-ts] (content (.toLocaleString ts))})


(deftemplate bookmarks "templates/bookmarks.html"
  [app owner state]
  {[:#bm-header] (content "Recent bookmarks")
   [:#url-input] (do-> (set-attr :value (:url-input-text state))
                       (listen :on-change #(handle-text-change % owner :url-input-text)
                               :on-key-down #(if (= (.-keyCode %) 10)
                                               (do
                                                 (if (clojure.string/blank? (:url-input-text state))
                                                   (println "no input")
                                                   (do
                                                     (add-bookmark owner)
                                                     (om/set-state! owner :url-input-text ""))))
                                                  (when (= (.-which %) 13)
                                                    (when (.-ctrlKey %)
                                                      (do
                                                        (if (clojure.string/blank? (:url-input-text state))
                                                          (println "no input")
                                                          (do
                                                            (add-bookmark owner)
                                                            (om/set-state! owner :url-input-text "")))))))))
   [:#url-list] (content (map #(url %) (sort-by :ts > app)))
   [:#bookmark-btn] (listen
                     :on-click
                     (fn [e]
                       (do
                         (if (clojure.string/blank? (:url-input-text state))
                           (println "no input")
                           (do
                             (add-bookmark owner)
                             (om/set-state! owner :url-input-text ""))))))})


;; --- INIT ---
(defn bookmark-view [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:url-input-text ""
       :ws-in (chan)})
    om/IWillMount
    (will-mount [_]
      (go
        (let [{:keys [in out] :as conn} (<! (connect!
                        (str (if ssl? "wss://" "ws://")
                             (.getDomain uri)
                             (when (= (.getDomain uri) "localhost")
                               (str ":" 8087 #_(.getPort uri)))
                             "/bookmark/ws")))]
          (om/set-state! owner :ws-in in)
          (>! in {:topic :get-user-bookmarks :data "eve@topiq.es"})
          (loop [{:keys [topic data]} (<! out)]
            (println "OUT" data)
            (case topic
              :get-user-bookmarks (om/transact! app :bookmarks (fn [old] data))
              :add-bookmark (om/transact! app :bookmarks (fn [old] (into data old)))
              :unknown)
            (recur (<! out))))))
    om/IRenderState
    (render-state [this state]
      (bookmarks (:bookmarks app) owner state))))


(om/root
 bookmark-view
 app-state
 {:target (. js/document (getElementById "center-container"))})

(ns lesezeichen.core
  (:require [figwheel.client :as figw :include-macros true]
            [weasel.repl :as ws-repl]
            [cljs.core.async :refer [put! chan <! >! alts! timeout close!] :as async]
            [cljs.reader :refer [read-string] :as read]
            [kioo.om :refer [content set-attr do-> substitute listen]]
            [kioo.core :refer [handle-wrapper]]
            [om.core :as om :include-macros true]
            [om.dom :as omdom])
  (:require-macros [kioo.om :refer [defsnippet deftemplate]]
                   [cljs.core.async.macros :refer [go go-loop]]))


(enable-console-print!)

(println "ALL HAIL TO KONNY!")

(def uri (goog.Uri. js/location.href))

(def ssl? (= (.getScheme uri) "https"))

;; fire up repl
#_(do
    (ns weasel.startup)
    (require 'weasel.repl.websocket)
    (cemerick.piggieback/cljs-repl
        :repl-env (weasel.repl.websocket/repl-env
                   :ip "0.0.0.0" :port 17782)))


;; weasel websocket
#_(if (= "localhost" (.getDomain uri))
  (do
    (figw/watch-and-reload
     ;; :websocket-url "ws://localhost:3449/figwheel-ws" default
     :jsload-callback (fn [] (print "reloaded")))
    (ws-repl/connect "ws://localhost:17782" :verbose true)))



;; --- DATABASE ---

(defn get-bookmarks [stage]
  (when stage nil))


(defn add-bookmark [owner]
  (let [stage (om/get-state owner :stage)
        url (om/get-state owner :url-input-text)]
    (when owner nil)))



;; --- MAIN VIEW ---

(defn handle-text-change [e owner text]
  (om/set-state! owner text (.. e -target -value)))


(defsnippet url "templates/bookmarks.html" [:.list-group-item]
  [bookmark]
  {[:.url-text] (do->
                 (set-attr :href (:url bookmark))
                 (content (:url bookmark)))
   [:.url-ts] (content (.toLocaleString (:ts bookmark)))})


(deftemplate bookmarks "templates/bookmarks.html"
  [app owner state]
  {[:#bm-header] (content "Recent bookmarks")
   [:#url-input] (do-> (set-attr :value (:url-input-text state))
                       (listen :on-change #(do (println (:url-input-text state))
                                               (handle-text-change % owner :url-input-text))
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
   [:#url-list] (content (map #(url %) (sort-by :ts > (get-bookmarks app))))
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
       :stage stage})
    om/IRenderState
    (render-state [this state]
      (bookmarks app owner state))))

(om/root
 bookmark-view
 (get-in @stage [:volatile :val-atom])
 {:target (. js/document (getElementById "center-container"))})

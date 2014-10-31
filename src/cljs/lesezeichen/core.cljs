(ns lesezeichen.core
  (:require [figwheel.client :as figw :include-macros true]
            [cljs.core.async :refer [put! chan <! >! alts! timeout close!] :as async]
            [cljs.reader :refer [read-string] :as read]
            [kioo.om :refer [content set-attr do-> substitute listen]]
            [kioo.core :refer [handle-wrapper]]
            [om.core :as om :include-macros true]
            [clojure.string :refer [lower-case trim blank?]]
            [om.dom :as omdom]
            [lesezeichen.com :refer [connect!]])
  (:require-macros [kioo.om :refer [defsnippet deftemplate]]
                   [cljs.core.async.macros :refer [go go-loop]]))


(enable-console-print!)

(def uri (goog.Uri. js/location.href))

(def ssl? (= (.getScheme uri) "https"))

(def app-state (atom {:bookmarks []
                      :user nil
                      :ws []}))

;; weasel websocket, development only
(if (= "localhost" (.getDomain uri))
  (do
    (figw/watch-and-reload
     :jsload-callback (fn [] (print "reloaded")))))


;; --- HELPER FUNCTIONS ---
(defn handle-text-change
  "Store and update input text in view component"
  [e owner text]
  (om/set-state! owner text (.. e -target -value)))


(defn send-bookmark
  "Send bookmark via websocket to server"
  [app owner]
  (let [url (om/get-state owner :url-input-text)
        {[in _] :ws user :user} (-> app deref)]
    (if (clojure.string/blank? url)
      (println "INFO: no input")
      (if user
        (do
          (go (>! in {:topic :add-bookmark :data {:email user :url url}}))
          (om/set-state! owner :url-input-text ""))
        (println "not registered yet!")))))


(defn send-registry
  "Send mail for sign up"
  [app owner]
  (let [email (om/get-state owner :signup-text)
        [in _] (-> app deref :ws)]
    (if (clojure.string/blank? email)
      (println "INFO: no mail")
      (do
        (go (>! in {:topic :sign-up :data {:email email}}))
        (om/transact! app :user (fn [old] email))
        (om/set-state! owner :signup-text "")))))


;; --- NAVBAR ---

(deftemplate nav "templates/navbar.html"
  [app owner state]
  {[:#brand] (content "Lesezeichen")
   [:#sign-up-input] (do-> (set-attr :value (:signup-text state))
                           (listen :on-change #(handle-text-change % owner :signup-text)
                                   :on-key-down #(if (= (.-keyCode %) 10)
                                                   (send-registry app owner)
                                                   (when (= (.-which %) 13)
                                                     (when (.-ctrlKey %)
                                                       (send-registry app owner))))))
   [:#modal-signup-btn] (listen :on-click (fn [e] (send-registry app owner)))})


;; --- MAIN VIEW ---

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
                                               (send-bookmark app owner)
                                               (when (= (.-which %) 13)
                                                 (when (.-ctrlKey %)
                                                   (send-bookmark app owner))))))
   [:#search-input] (do-> (set-attr :value (:search-text state))
                          (listen :on-change #(handle-text-change % owner :search-text)))
   [:#url-list] (content (let [bms (if (blank? (:search-text state))
                                     (:bookmarks app)
                                     (remove
                                      (fn [bookmark]
                                        (nil?
                                         (re-find
                                          (-> state :search-text trim lower-case re-pattern)
                                          (-> bookmark :title lower-case))))
                                      (:bookmarks app)))]
                           (map #(url %) (sort-by :ts > bms))))
   [:#bookmark-btn] (listen :on-click (fn [e] (send-bookmark app owner)))})


;; --- VIEWS ---
(defn nav-view
  "Navbar view handling sin-up and log-in menu"
  [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:signup-text ""})
    om/IWillMount
    (will-mount [_]
      (go
        (let [{:keys [in out] :as conn} (<! (connect!
                        (str (if ssl? "wss://" "ws://")
                             (.getDomain uri)
                             (when (= (.getDomain uri) "localhost")
                               (str ":" 8087 #_(.getPort uri)))
                             "/bookmark/ws")))]
          (om/transact! app :ws (fn [old] [in out]))
          (>! in {:topic :get-user-bookmarks :data "konny@topiq.es"})
          (loop [{:keys [topic data]} (<! out)]
            (case topic
              :get-user-bookmarks (om/transact! app :bookmarks (fn [old] data))
              :add-bookmark (om/transact! app :bookmarks (fn [old] (into data old)))
              :unknown)
            (if-let [package (<! out)]
              (recur package))))))
    om/IRenderState
    (render-state [this state]
      (nav app owner state))))



(defn bookmark-view
  "Central view containing bookmarks and url input field"
  [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:url-input-text ""
       :search-text ""})
    om/IRenderState
    (render-state [this state]
      (bookmarks app owner state))))


;; --- ROOT ---

(om/root
 nav-view
 app-state
 {:target (. js/document (getElementById "navbar-container"))})

(om/root
 bookmark-view
 app-state
 {:target (. js/document (getElementById "center-container"))})

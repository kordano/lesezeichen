(ns lesezeichen.client.core
  (:require [figwheel.client :as figw :include-macros true]
            [cljs.core.async :refer [put! chan <! >! alts! timeout close!] :as async]
            [cljs.reader :refer [read-string] :as read]
            [kioo.om :refer [content set-attr do-> substitute listen remove-attr add-class remove-class]]
            [kioo.core :refer [handle-wrapper]]
            [om.core :as om :include-macros true]
            [clojure.string :refer [lower-case trim blank?]]
            [om.dom :as omdom]
            [chord.client :refer [ws-ch]])
  (:require-macros [kioo.om :refer [defsnippet deftemplate]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(def uri (goog.Uri. js/location.href))

(def ssl? (= (.getScheme uri) "https"))

(def app-state (atom {:bookmarks []
                      :user {:email nil :token-status nil}
                      :ws []}))

(def socket-url (str (if ssl? "wss://" "ws://")
                     (.getDomain uri)
                     (when (= (.getDomain uri) "localhost")
                       (str ":" 8087 #_(.getPort uri)))
                     "/bookmark/ws"))

;; weasel websocket, development only
#_(if (= "localhost" (.getDomain uri))
  (do
    (figw/watch-and-reload
     :jsload-callback (fn [] (print "reloaded")))))


;; --- HELPER FUNCTIONS ---
(defn handle-text-change
  "Store and update input text in view component"
  [e owner text]
  (om/set-state! owner text (.. e -target -value)))


(defn show-signup-dialog
  "SHow and hide signup-dialog"
  [app owner]
  (let [signup-dialog-status (om/get-state owner :sign-up-dialog)]
    (om/set-state! owner :sign-up-dialog (not signup-dialog-status))))


(defn get-local-store
  "Retrieve data from local html storage"
  [db]
  {:email (.getItem db "email")
   :token (.getItem db "token")})


(defn send-bookmark
  "Send bookmark via websocket to server"
  [app owner]
  (let [url (om/get-state owner :url-input-text)
        {ws :ws {:keys [email token-status token]} :user} (-> app deref)]
    (if (clojure.string/blank? url)
      (println "INFO: no input")
      (if (= :valid token-status)
        (do
          (go (>! ws {:topic :add-bookmark :data {:email email :url url} :token token}))
          (om/set-state! owner :url-input-text ""))
        (show-signup-dialog app owner)))))


(defn send-registry
  "Send mail for sign up"
  [app owner]
  (let [email (om/get-state owner :signup-text)
        ws (-> app deref :ws)]
    (if (clojure.string/blank? email)
      (println "INFO: no mail")
      (do
        (go (>! ws {:topic :sign-up :data {:email email}}))
        (om/transact! app :user (fn [old] email))
        (om/set-state! owner :signup-text "")))))


;; --- NAVBAR ---
(deftemplate nav "templates/navbar.html"
  [app owner state]
  {[:#brand] (content "Lesezeichen")
   [:#nav-current-user] (content (or (-> app :user :email) "Not registered yet!"))
   [:#signup-modal] (if (om/get-state owner :sign-up-dialog)
                      (add-class :in)
                      (remove-class :in))
   [:#sign-up-input] (do-> (set-attr :value (:signup-text state))
                           (listen :on-change #(handle-text-change % owner :signup-text)
                                   :on-key-down #(if (= (.-keyCode %) 10)
                                                   (send-registry app owner)
                                                   (when (= (.-which %) 13)
                                                     (when (.-ctrlKey %)
                                                       (send-registry app owner))))))
   [:#modal-signup-btn] (listen :on-click (fn [e] (send-registry app owner)))
   [:#general-info] (content (om/get-state owner :info-text))
   [:#clear-db-btn] (listen :on-click (fn [e] (do (.clear (.-localStorage js/window))
                                                 (om/transact! app :user (fn [old] (assoc old :email "")))
                                                 (println "Info: Store cleared!"))))})



;; --- MAIN VIEW ---
(defsnippet url "templates/bookmarks.html" [:.list-group-item]
  [{:keys [email title url ts]}]
  {[:.url-text] (do-> (set-attr :href url)
                      (content (if (= "" title) url title)))
   ;;[:.url-user] (content email)
   [:.url-ts] (content (.toLocaleString ts))})


#_(defsnippet welcome "templates/welcome" [:#register-message]
  [app owner state]
  {[:#sign-up-username] (do-> (set-attr :value (:sign-up-username state))
                              (listen :on-change #(handle-text-change % owner :sign-up-username)))
   [:#sign-up-mail] (do-> (set-attr :value (:sign-up-email state))
                          (listen :on-change #(handle-text-change % owner :sign-up-email)
                                  :on-key-down #(if (= (.-keyCode %) 10)
                                                  (send-registry app owner)
                                                  (when (= (.-which %) 13)
                                                    (when (.-ctrlKey %)
                                                      (send-registry app owner))))))})


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
   [:#search-input]  (do-> (set-attr :value (:search-text state))
                           #_(when (:valid (-> app :user :token-status)) (remove-attr :disabled))
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
      {:signup-text ""
       :sign-up-dialog false
       :info-text ""})
    om/IWillMount
    (will-mount [_]
      (let [local-store (get-local-store (.-localStorage js/window))]
        (go
          (let [{:keys [ws-channel error] :as ws-conn} (<! (ws-ch socket-url))]
            (om/transact! app :ws (fn [old new] ws-channel) ws-channel)
            (if-not error
              (do
                ;; verify token
                (if-not (or (:token local-store) (:email local-store))
                  (println "Not registered yet!")
                  (do
                    (om/transact! app :user (fn [old new] (assoc-in old [:email] (:email local-store))))
                    (om/transact! app :user (fn [old new] (assoc-in old [:token] (:token local-store))))
                    (>! ws-channel {:topic :verify-token :data (:email local-store) :token (:token local-store)})))
                ;; listen to incoming messages
                (loop [{{:keys [topic data] :as message} :message error :error} (<! ws-channel)]
                  (if-not error
                    (do
                      (println "Incoming Message: " (pr-str message))
                      (case topic
                        :get-user-bookmarks (om/transact! app :bookmarks (fn [old] data))
                        :get-all-bookmarks (om/transact! app :bookmarks (fn [old] data))
                        :sign-up (go
                                   (om/set-state! owner :info-text "User created, email sent!")
                                   (<! (timeout 5000))
                                   (om/set-state! owner :info-text ""))
                        :add-bookmark (om/transact! app :bookmarks (fn [old] (into data old)))
                        :verify-token (do
                                        (om/transact! app :user (fn [old new] (assoc-in old [:token-status] data)))
                                        (case data
                                          :valid (>! ws-channel {:topic :get-user-bookmarks :data (:email local-store) :token (:token local-store)})
                                          :invalid (om/set-state! owner :info-text "invalid token")
                                          :expired (om/set-state! owner :info-text "token expired")))
                        (println "Unknown topic"))
                      (if-let [from-server (<! ws-channel)]
                        (recur from-server)))
                    (println "Error:" (pr-str error)))))
              (println "Error:" (pr-str error)))))))
    om/IRenderState
    (render-state [this state]
      (nav app owner state))))



(defn bookmark-view
  "Central view containing bookmarks and url input field"
  [app owner]
  (reify
    om/IInitState
    (init-state [_] {:url-input-text ""
                     :search-text ""
                     :sign-up-mail ""
                     :sign-up-username ""})
    om/IRenderState
    (render-state [this state] (bookmarks app owner state))))


;; --- ROOT ---

(om/root
 nav-view
 app-state
 {:target (. js/document (getElementById "navbar-container"))})


(om/root
 bookmark-view
 app-state
 {:target (. js/document (getElementById "center-container"))})

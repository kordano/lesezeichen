(ns lesezeichen.auth.core
  (:require [cljs.core.async :refer [put! chan <! >! alts! timeout close!] :as async]
            [cljs.reader :refer [read-string] :as read]
            [kioo.om :refer [content set-attr do-> substitute listen]]
            [kioo.core :refer [handle-wrapper]]
            [om.core :as om :include-macros true]
            [clojure.string :refer [split]]
            [om.dom :as omdom]
            [chord.client :refer [ws-ch]])
  (:require-macros [kioo.om :refer [defsnippet deftemplate]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(def app-state (atom {:ws nil :auth nil}))

(def uri (goog.Uri. js/location.href))

(def ssl? (= (.getScheme uri) "https"))

(def socket-url (str (if ssl? "wss://" "ws://")
                     (.getDomain uri)
                     (when (= (.getDomain uri) "localhost")
                       (str ":" 8087 #_(.getPort uri)))
                     "/bookmark/ws"))

(defn parse-params
  "Parse URL parameters into a hashmap"
  []
  (let [param-strs (-> (.-location js/window) (split #"\?") last (split #"\&"))]
    (into {} (for [[k v] (map #(split % #"=") param-strs)]
               [(keyword k) v]))))


(deftemplate auth-jumbo "templates/auth.html"
  [app owner state]
  {[:#user-name] (content (:email-text state))
   [:#jumbo-text] (content (:jumbo-text state))})

(defn get-local-store [db]
  {:email (.getItem db "email")
   :token (.getItem db "token")})

(defn auth-view
  "Authentication notification"
  [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:jumbo-text ""
       :email-text "user"})
    om/IWillMount
    (will-mount [_]
      (let [user-params (parse-params)
            db (.-localStorage js/window)
            local-store (get-local-store db)]
        (if-not (:token local-store)
          (do
            (om/set-state! owner :email-text (:email user-params))
            (.setItem db "email" (:email user-params))
            (go
              (let [{:keys [ws-channel error]} (<! (ws-ch socket-url))]
                (if-not error
                  (>! ws-channel {:topic :register-device :data user-params})
                  (js/console.log "Error:" (pr-str error)))
                (let [{:keys [message error]} (<! ws-channel)]
                  (if error
                    (do
                      (om/set-state! owner :jumbo-text "Something went wrong with your socket connetion!")
                      (println (pr-str "Error: " error)))
                    (if-not (= (:data message) :registry-failed)
                      (do
                        (.setItem db "token" (:data message))
                        (om/set-state! owner :jumbo-text "Congratulations, your device has been registered. Now if you visit lesezeichen, you will be automatically logged in."))
                      (om/set-state! owner :jumbo-text "Registration failed. Wrong email/authentication combination. Please check your link again in your registration email.")))))))
          (if-not (= (:email user-params) (:email local-store))
            (println "Error: Local user is not signed-up user")
            (do
              (om/set-state! owner :email-text (:email user-params))
              (om/set-state! owner :jumbo-text "Your device is already registered. Move along."))))))
    om/IRenderState
    (render-state [this state]
      (auth-jumbo (:auth app) owner state))))


(om/root
 auth-view
 app-state
 {:target (. js/document (getElementById "center-container"))})

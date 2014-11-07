(ns lesezeichen.auth.core
  (:require [figwheel.client :as figw :include-macros true]
            [cljs.core.async :refer [put! chan <! >! alts! timeout close!] :as async]
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

(def app-state (atom {:ws nil
                      :email ""} ))

(def uri (goog.Uri. js/location.href))

(def ssl? (= (.getScheme uri) "https"))


;; weasel websocket, development only
#_(if (= "localhost" (.getDomain uri))
  (do
    (figw/watch-and-reload
     :jsload-callback (fn [] (print "reloaded")))))

(defn parse-params
  "Parse URL parameters into a hashmap"
  []
  (let [param-strs (-> (.-location js/window) (split #"\?") last (split #"\&"))]
    (into {} (for [[k v] (map #(split % #"=") param-strs)]
               [(keyword k) v]))))


(deftemplate auth-jumbo "templates/auth.html"
  [app owner]
  {[:#user-name] (content (:email ap))})


(println (parse-params))

(defn auth-view
  "Authentication notification"
  [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (om/transact! app :email (fn [old new] (:email (parse-params)))))
    om/IRenderState
    (render-state [this state]
      (auth-jumbo app owner))))


(om/root
 auth-view
 app-state
 {:target (. js/document (getElementById "center-container"))})

(ns lesezeichen.core
  (:require [figwheel.client :as figw :include-macros true]
            [weasel.repl :as ws-repl]
            [hasch.core :refer [uuid]]
            [datascript :as d]
            [geschichte.stage :as s]
            [geschichte.sync :refer [client-peer]]
            [konserve.store :refer [new-mem-store]]
            [geschichte.p2p.auth :refer [auth]]
            [geschichte.p2p.fetch :refer [fetch]]
            [geschichte.p2p.publish-on-request :refer [publish-on-request]]
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


(def eval-fn {'(fn replace [old params] params) (fn replace [old params] params)
              '(fn [old params] (:db-after (d/transact old params)))
              (fn [old params] (:db-after (d/transact old params)))})


; we can do this runtime wide here, since we only use this datascript version
(read/register-tag-parser! 'datascript/DB datascript/db-from-reader)
(read/register-tag-parser! 'datascript/Datom datascript/datom-from-reader)


(def trusted-hosts (atom #{:geschichte.stage/stage (.getDomain uri)}))

(defn- auth-fn [users]
  (go (js/alert (pr-str "AUTH-REQUIRED: " users))
    {"eve@polyc0l0r.net" "lisp"}))


;; --- DATABASE ---

(defn get-bookmarks [stage]
  (let [db (om/value
            (get-in
             stage
             ["eve@polyc0l0r.net"
              #uuid "84026416-bea6-409d-9167-37d30b49d55a"
              "master"]))
        query  '[:find ?p ?url ?user ?ts
                 :where
                 [?p :url ?url]
                 [?p :user ?user]
                 [?p :ts ?ts]]]
    (map (partial zipmap [:id :url :user :ts])
         (d/q query db))))


(defn add-bookmark [owner]
  (let [stage (om/get-state owner :stage)
        url (om/get-state owner :url-input-text)]
   (go
     (<! (s/transact
          stage
          ["eve@polyc0l0r.net"
           #uuid "84026416-bea6-409d-9167-37d30b49d55a"
           "master"]
          [{:db/id (uuid)
            :url url
            :user "kordano@hushmail.com"
            :ts (js/Date.)}]
          '(fn [old params] (:db-after (d/transact old params)))))
     (<! (s/commit! stage
                    {"eve@polyc0l0r.net"
                     {#uuid "84026416-bea6-409d-9167-37d30b49d55a"
                      #{"master"}}})))))



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
(go
  (def store (<! (new-mem-store
                  (atom
                   (read-string
                    "{#uuid \"1c790d98-ab31-58be-94b8-408c3c39cca4\" #datascript/DB {:schema {:bookmarks {:db/cardinality :db.cardinality/many}, :users {:db/cardinality :db.cardinality/many}}, :datoms []}, #uuid \"123ed64b-1e25-59fc-8c5b-038636ae6c3d\" (fn replace [old params] params), #uuid \"0c797a78-0821-5b74-8688-ec5bacec09c8\" {:transactions [[#uuid \"1c790d98-ab31-58be-94b8-408c3c39cca4\" #uuid \"123ed64b-1e25-59fc-8c5b-038636ae6c3d\"]], :parents [], :ts #inst \"2014-08-19T10:12:44.265-00:00\", :author \"eve@polyc0l0r.net\"}, \"eve@polyc0l0r.net\" {#uuid \"84026416-bea6-409d-9167-37d30b49d55a\" {:description \"bookmarks\", :schema {:type \"http://github.com/ghubber/geschichte\", :version 1}, :pull-requests {}, :causal-order {#uuid \"0c797a78-0821-5b74-8688-ec5bacec09c8\" []}, :public false, :branches {\"master\" #{#uuid \"0c797a78-0821-5b74-8688-ec5bacec09c8\"}}, :head \"master\", :last-update #inst \"2014-08-19T10:12:44.265-00:00\", :id #uuid \"84026416-bea6-409d-9167-37d30b49d55a\"}}}")
                   (atom {'datascript/Datom datascript/datom-from-reader
                          'datascript/DB datascript/db-from-reader})))))
  (def peer (client-peer "CLIENT-PEER" store (comp (partial publish-on-request store)
                                                   (partial fetch store)
                                                   (partial auth store (fn [users] {"eve@polyc0l0r.net" "lisp"}) (fn [token] true)))))


  (def stage (<! (s/create-stage! "eve@polyc0l0r.net" peer eval-fn)))

  (<! (s/subscribe-repos! stage {"eve@polyc0l0r.net"
                           {#uuid "84026416-bea6-409d-9167-37d30b49d55a"
                            #{"master"}}}))

  (<! (s/connect!
       stage
       (str
        (if ssl?  "wss://" "ws://")
        (.getDomain uri)
        (when (= (.getDomain uri) "localhost")
          (str ":" 8087 #_(.getPort uri)))
        "/geschichte/ws")))

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
   {:target (. js/document (getElementById "center-container"))}))





(comment

  (-> @stage :volatile :peer deref :volatile :store :state deref)

  (-> @stage :volatile :val-atom deref)

)

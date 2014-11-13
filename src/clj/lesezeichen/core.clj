(ns lesezeichen.core
  (:gen-class :main true)
  (:require [clojure.edn :as edn]
            [net.cgrand.enlive-html :refer [content deftemplate defsnippet set-attr substitute html] :as enlive]
            [clojure.java.io :as io]
            [taoensso.timbre :refer [debug]]
            [compojure.route :refer [resources]]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.handler :refer [site api]]
            [aprint.core :refer [aprint]]
            [lesezeichen.db :refer :all]
            [org.httpkit.server :refer [with-channel on-close on-receive run-server send!]]
            [clojure.core.async :refer [chan >! <! go go-loop put! close!]]
            [ring.util.response :as resp]))


(def server-state (atom {:authenticated-tokens {}}))


(deftemplate static-page
  (io/resource "public/index.html")
  []
  [:#bootstrap-css] (set-attr "href" "static/bootstrap/readable/bootstrap.min.css")
  [:#react-js] (set-attr "src" "static/react/react-0.9.0.min.js")
  [:#jquery-js] (set-attr "src" "static/jquery/jquery-1.11.0.min.js")
  [:#bootstrap-js] (set-attr "src" "static/bootstrap/bootstrap-3.1.1-dist/js/bootstrap.min.js")
  [:#js-files] (substitute (html [:script {:src "js/main.js" :type "text/javascript"}])))


(deftemplate dev-page
  (io/resource "public/index.html")
  [build]
  [:#goog-base-js] (set-attr :src (str "js/compiled/" (name build) "/out/goog/base.js"))
  [:#main-js] (set-attr :src (str "js/compiled/" (name build) "/main.js"))
  [:#js-require] (substitute (html [:script {:type "text/javascript"}
                                 (str "goog.require('lesezeichen." (name build) ".core');")])))


(defn fetch-url [url]
  (try
    (enlive/html-resource (java.net.URL. url))
    (catch Exception e :error)))


(defn fetch-url-title
  "fetch url and extract title"
  [url]
  (let [res (fetch-url url)]
    (if (= :error res)
      url
      (-> res (enlive/select [:head :title]) first :content first))))


(defn authorized? [state {:keys [topic token]}]
  (if (#{:sign-up :register-device :verify-token :error} topic)
    true
    (= :valid (-> @state :authenticated-tokens (get token) :status))))


(defn handle-token [state channel {:keys [topic data token] :as msg}]
  (let [token-status (verify-token (:conn @state) data token)]
    (swap! state assoc-in [:authenticated-tokens token] {:user data :status token-status})
    {:topic topic :data token-status}))


(defn dispatch [state channel {:keys [topic data token] :as msg}]
  (let [conn (:conn @state)]
      (if (authorized? state msg)
        (case topic
          :get-user-bookmarks {:topic topic :data (get-user-bookmarks conn data)}
          :get-all-bookmarks {:topic topic :data (get-all-bookmarks conn)}
          :sign-up {:topic topic :data (add-user conn data)}
          :register-device {:topic topic :data (register-device conn data)}
          :add-bookmark {:topic topic :data (add-bookmark conn (assoc data :title (fetch-url-title (:url data))))}
          :verify-token (handle-token state channel msg)
          {:topic :error :data :unknown-request})
        {:topic :error :data :not-authorized})))


(defn bookmark-handler
  "Handle incoming requests"
  [request]
  (with-channel request channel
    (on-close channel
              (fn [status]
                (swap!
                 server-state update-in [:authenticated-tokens]
                 (fn [token-map ch] (dissoc token-map ch))
                 channel)))
    (on-receive channel
                (fn [msg]
                  (let [in-msg (read-string msg)
                        {:keys [topic data token] :as out-msg} (str (dispatch server-state channel in-msg))
                        ]
                    (debug (str "Message received: " msg))
                    (send! channel out-msg)
                    (debug (str "Message sent: " out-msg))
                    (when (= :add-bookmark topic)
                      (doall
                       (map
                        #(send! (:channel %) out-msg)
                        (-> @server-state
                            :authenticated-tokens
                            (dissoc (:token msg))
                            vals)))))))))


(defroutes handler
  (resources "/")
  (GET "/bookmark/ws" [] bookmark-handler)
  (GET "/*" {{token :auth email :email} :params}
       (if (or token email)
         (dev-page :auth)
         (if (= (:build @server-state) :prod)
           (static-page)
           (dev-page :client)))))


(defn read-config [state path]
  (let [config (-> path slurp read-string)]
    (swap! state merge config))
  state)


(defn init-db [state]
  (let [conn (scratch-conn)]
    (swap! state #(assoc %1 :conn %2) conn))
  (init-schema (:conn @state) (:schema @state))
  state)


(defn init
  "Read in config file, create sync store and peer"
  [state path]
  (-> state
      (read-config path)
      init-db))


(defn -main [config-path & args]
  (init server-state config-path)
  (when (:cold-start @server-state)
    (init-schema (:conn @server-state) (:schema @server-state))
    (add-user (:conn @server-state) {:email "eve@topiq.es"}))
  (run-server (site #'handler) {:port (:port @server-state) :join? false}))


(comment

  (init server-state "resources/server-config.edn")

  (def server (run-server (site #'handler) {:port (:port @server-state) :join? false}))

  (server)

  ;; on first startup initialize datomic schema
  (init-schema (:schema @server-state))

  (get-all-users (:conn @server-state))

)

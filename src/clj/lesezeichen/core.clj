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
  [build]
  [:#bootstrap-css] (set-attr "href" "static/bootstrap/readable/bootstrap.min.css")
  [:#react-js] (set-attr "src" "static/react/react-0.9.0.min.js")
  [:#jquery-js] (set-attr "src" "static/jquery/jquery-1.11.0.min.js")
  [:#bootstrap-js] (set-attr "src" "static/bootstrap/bootstrap-3.1.1-dist/js/bootstrap.min.js")
  [:#js-files] (substitute (html [:script {:src (str "js/" (name build) ".js") :type "text/javascript"}])))


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


(defn authorized?
  "Check authorization status of given topic and token"
  [state {:keys [topic token]}]
  (if (#{:sign-up :register-device :verify-token :error} topic)
    true
    (= :valid (-> @state :authenticated-tokens (get token) :status))))


(defn handle-token
  "Handle currently connected browsers"
  [state channel {:keys [topic data token] :as msg}]
  (let [token-status (verify-token (:conn @state) data token)]
    (swap! state assoc-in [:authenticated-tokens token] {:user data :status token-status :channel channel})
    {:topic topic :data token-status}))


(defn dispatch
  "Handle incoming requests via sockets"
  [state channel {:keys [topic data token] :as msg}]
  (let [conn (:conn @state)]
      (if (authorized? state msg)
        (case topic
          :get-user-bookmarks {:topic topic :data (get-user-bookmarks conn data)}
          :get-all-bookmarks {:topic topic :data (get-all-bookmarks conn)}
          :sign-up {:topic topic :data (add-user conn (assoc data :host (:mail-host @state) :port (:mail-port @state) :host-name (:host-name @state)))}
          :register-device {:topic topic :data (register-device conn data)}
          :add-bookmark {:topic topic :data (add-bookmark conn (assoc data :title (fetch-url-title (:url data))))}
          :verify-token (handle-token state channel msg)
          {:topic :error :data :unknown-request})
        {:topic :error :data :not-authorized})))


(defn bookmark-handler
  "Socket handling"
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
                        {:keys [topic data token] :as out-msg} (dispatch server-state channel in-msg)]
                    (send! channel (str out-msg))
                    (when (= :add-bookmark topic)
                      (doall
                       (map
                        #(send! (:channel %) (str out-msg))
                        (-> @server-state :authenticated-tokens (dissoc (:token in-msg)) vals)))))))))


(defroutes handler
  (resources "/")
  (GET "/bookmark/ws" [] bookmark-handler)
  (GET "/*" {{auth-code :auth email :email} :params}
       (if (= (:build @server-state) :prod)
         (if (or auth-code email)
           (static-page :auth)
           (static-page :client))
         (if (or auth-code email)
           (dev-page :auth)
           (dev-page :client)))))


(defn read-config [state path]
  (let [config (-> path slurp read-string)]
    (swap! state merge config))
  state)


(defn init-db [state]
  (let [conn (db-conn)]
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
  (debug @server-state)
  (run-server (site #'handler) {:port (:port @server-state) :join? false}))


(comment

  (init server-state "resources/server-config.edn")

  (def server (run-server (site #'handler) {:port (:port @server-state) :join? false}))

  (server)

)

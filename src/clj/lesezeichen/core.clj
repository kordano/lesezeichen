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

(def server-state (atom {:out-chans []}))


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


(defn dispatch [{:keys [topic data]}]
  (case topic
    :get-user-bookmarks {:topic topic :data (get-user-bookmarks (:conn @server-state) data)}
    :sign-up  {:topic topic
               :data (add-user (:conn @server-state) data)}
    :register-device {:topic topic
                      :data (register-device (:conn @server-state) data)}
    :add-bookmark {:topic topic
                   :data (add-bookmark (:conn @server-state)
                                       (assoc data :title (fetch-url-title (:url data))))}
    :verify-token {:topic topic :data (verify-token (:conn @server-state) data)}
    {:topic :error :data :unknown-request}))


(defn bookmark-handler
  "Handle incoming requests"
  [request]
  (let [out-ch (chan)]
    (with-channel request channel
      (swap! server-state update-in [:out-chans] conj out-ch)
      (go-loop [m (<! out-ch)]
        (when m
          (send! channel m)
          (recur (<! out-ch))))
      (on-close channel (fn [status]
                          (swap! server-state update-in [:out-chans]
                                 (fn [old new] (vec (remove #(= new %) old)))
                                 out-ch)
                          (close! out-ch)))
      (on-receive channel (fn [msg] (let [data (str (dispatch (read-string msg)))]
                                     (debug (str "Message received: " msg))
                                     (send! channel data)
                                     (doall
                                      (map
                                       #(put! % data)
                                       (remove #{out-ch} (:out-chans @server-state))))))))))


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


(defn -main [& args]
  (init server-state (first args))
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

  (get-user-bookmarks (:conn @server-state) "eve@topiq.es")

)

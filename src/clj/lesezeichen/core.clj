(ns lesezeichen.core
  (:gen-class :main true)
  (:require [clojure.edn :as edn]
            [net.cgrand.enlive-html :refer [deftemplate set-attr substitute html] :as enlive]
            [clojure.java.io :as io]
            [compojure.route :refer [resources]]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.handler :refer [site api]]
            [lesezeichen.db :refer :all]
            [org.httpkit.server :refer [with-channel on-close on-receive run-server send!]]
            [ring.util.response :as resp]))

(def server-state (atom nil))

(deftemplate static-page
  (io/resource "public/index.html")
  []
  [:#bootstrap-css] (set-attr "href" "static/bootstrap/readable/bootstrap.min.css")
  [:#react-js] (set-attr "src" "static/react/react-0.9.0.min.js")
  [:#jquery-js] (set-attr "src" "static/jquery/jquery-1.11.0.min.js")
  [:#bootstrap-js] (set-attr "src" "static/bootstrap/bootstrap-3.1.1-dist/js/bootstrap.min.js")
  [:#js-files] (substitute (html [:script {:src "js/main.js" :type "text/javascript"}])))


(defn fetch-url [url]
  (try
    (enlive/html-resource (java.net.URL. url))
    (catch Exception e "FAILED")))


(defn fetch-url-title
  "fetch url and extract title"
  [url]
  (let [res (fetch-url url)]
    (if (= "FAILED" res)
      url
      (-> res (enlive/select [:head :title]) first :content first))))


(defn dispatch [{:keys [topic data]}]
  (case topic
    :get-user-bookmarks {:topic topic
                         :data (get-user-bookmarks (:conn @server-state) data)}
    :add-bookmark {:topic topic
                   :data (add-bookmark (:conn @server-state)
                                       (assoc data :title (fetch-url-title (:url data))))}
    {:topic :error :data :unknown-request}))


(defn bookmark-handler
  "Handle incoming requests"
  [request]
  (with-channel request channel
    (on-close channel (fn [status] (println "tweet channel closed: " status "!")))
    (on-receive channel (fn [msg]
                          (send! channel (str (dispatch (read-string msg))))))))

(defroutes handler
  (resources "/")
  (GET "/bookmark/ws" [] bookmark-handler)
  (GET "/*" [] (if (= (:build @server-state) :prod)
                 (static-page)
                 (io/resource "public/index.html"))))


(defn read-config [state path]
  (let [config (-> path slurp read-string)]
    (swap! state merge config))
  state)


(defn init-db [state]
  (swap! state #(assoc %1 :conn %2) (db-conn))
  state)

(defn init
  "Read in config file, create sync store and peer"
  [state path]
  (-> state
      (read-config path)
      init-db))


(defn -main [& args]
  (init server-state (first args))
  (init-schema (:schema @server-state))
  (run-server (site #'handler) {:port (:port @server-state) :join? false}))


(comment

  (init server-state "resources/server-config.edn")

  ;; on first startup initialize datomic schema
  (init-schema (:schema @server-state))

  (def server (start-server (:port @server-state)))

  (server)

)

(ns lesezeichen.db
  (:require [datomic.api :as d]
            [clojure.java.io :as io]
            [clj-time.core :as t]
            [postal.core :as postal]
            [aprint.core :refer [aprint]]
            [lesezeichen.io :refer [transact-all]]))


(defn generate-token []
  (let [chars (map char (concat (range 48 58) (range 65 91) (range 97 123)))]
    (apply str (take 10 (repeatedly #(rand-nth chars))))))

(defn send-registry [email auth-code]
  (postal/send-message
   {:from "authentication@topiq.es"
    :to [email]
    :subject "Registry token"
    :body [:alternative
           {:type "text/plain"
            :content (str "In order to activate your account please visit http://localhost:8087/?auth=" auth-code " again .")}
           {:type "text/html"
            :content (str "<html>In order to activate your account please visit <a href=http://localhost:8087/?auth=" auth-code ">lesezeichen</a> again.</html>")}]}))


(def db-uri-base "datomic:free://0.0.0.0:4334")

(defn scratch-conn
  "Create a connection to an anonymous, in-memory database."
  []
  (let [uri (str "datomic:mem://" (d/squuid))]
    (d/delete-database uri)
    (d/create-database uri)
    (d/connect uri)))


(defn db-conn
  ""
  []
  (let [uri (str db-uri-base "/lesezeichen")]
    (d/create-database uri)
    (d/connect uri)))


(defn init-schema [conn path]
  (transact-all conn (io/resource path)))


(defn add-user [conn {:keys [email]}]
  (let [auth-code (str (java.util.UUID/randomUUID))]
    (d/transact
     conn
     [{:db/id (d/tempid :db.part/user)
       :user/auth-code auth-code
       :user/email email}])
    (send-registry email auth-code)))


(defn- get-user-id [conn email]
  (let [query '[:find ?e
               :in $ ?email
               :where
                [?e :user/email ?email]]
        db (d/db conn)]
    (ffirst (d/q query db email))))


(defn transact-bookmark [conn {:keys [url title email]}]
  (let [uid (get-user-id conn email)]
    (d/transact
     conn
     [{:db/id (d/tempid :db.part/user)
       :bookmark/url url
       :bookmark/title title
       :bookmark/user uid}])))


(defn get-bookmark
  "Retrieve bookmark"
  [conn {:keys [url email]}]
  (let [query '[:find ?url ?title ?tx ?email
               :in $ ?url ?email
               :where
               [?bm :bookmark/url ?url ?tx]
               [?bm :bookmark/title ?title]
               [?bm :bookmark/user ?uid]
               [?uid :user/email ?email]]
        db (d/db conn)]
    (mapv
     (fn [bookmark]
       (update-in
        (zipmap [:url :title :ts :email] bookmark)
        [:ts]
        #(:db/txInstant (d/entity (d/db conn) %))))
     (d/q query db url email))))


(defn add-bookmark
  "Transact bookmark and return the resulting datom"
  [conn {:keys [url title email] :as bookmark}]
  (do
    (transact-bookmark conn bookmark)
    (get-bookmark conn bookmark)))


(defn get-user-bookmarks
  "Find user's bookmarks"
  [conn email]
  (let [query '[ :find ?url ?title ?tx
                :in $ ?email
                :where
                [?bm :bookmark/url ?url ?tx]
                [?bm :bookmark/title ?title]
                [?bm :bookmark/user ?uid]
                [?uid :user/email ?email]]
        db (d/db conn)]
    (mapv
     (fn [bookmark]
       (update-in
        (zipmap [:url :title :ts] bookmark)
        [:ts]
        #(:db/txInstant (d/entity (d/db conn) %))))
     (d/q query db email))))


(defn get-all-bookmarks
  "Retrieve all bookmarks"
  [conn]
  (map
   #(zipmap [:url :title :user] %)
   (d/q '[:find ?url ?title ?email
          :where
          [?bm :bookmark/url ?url]
          [?bm :bookmark/title ?title]
          [?bm :bookmark/user ?uid]
          [?uid :user/email ?email]]
        (d/db conn))))


(defn get-all-users
  "Retrieve all users"
  [conn]
  (map
   first
   (d/q '[:find ?email ?token
          :where
          [?e :user/email ?email]
          [?e :user/auth-code ?token]
          ]
        (d/db conn))))


(defn register-device [conn auth]
  (let [query '[:find ?email
                :in $ ?auth
                :where
                [?u :user/email ?email]
                [?u :user/auth-code ?auth]]
        db (d/db conn)]
    (ffirst (d/q query db auth))))


(comment

  (def conn (scratch-conn))

  (init-schema conn "schema.edn")


  )

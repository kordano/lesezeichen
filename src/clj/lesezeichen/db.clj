(ns lesezeichen.db
  (:require [datomic.api :as d]
            [clojure.java.io :as io]
            [clj-time.core :as t]
            [aprint.core :refer [aprint]]
            [lesezeichen.io :refer [transact-all]]))


(def db-uri-base "datomic:free://localhost:4334")


(defn- scratch-conn
  "Create a connection to an anonymous, in-memory database."
  []
  (let [uri (str db-uri-base (d/squuid))]
    (d/delete-database uri)
    (d/create-database uri)
    (d/connect uri)))

(def conn (d/connect (str db-uri-base "/lesezeichen")))


(defn init-schema [path]
  (transact-all conn (io/resource path)))


(defn add-user [{:keys [email]}]
  (d/transact
   conn
   [{:db/id (d/tempid :db.part/user)
     :user/email email}]))


(defn- get-user-id [email]
  (let [query '[:find ?e
               :in $ ?email
               :where
                [?e :user/email ?email]]
        db (d/db conn)]
    (ffirst (d/q query db email))))


(defn- get-tx-id [eid attr]
  (let [query '[:find ?tx
                :in $ ?e ?attr
                :where [?e ?attr _ ?tx]]
        db (d/db conn)]
    (d/q query db eid attr)))


(defn transact-bookmark [{:keys [url title email]}]
  (let [uid (get-user-id email)]
    (d/transact
     conn
     [{:db/id (d/tempid :db.part/user)
       :bookmark/url url
       :bookmark/title title
       :bookmark/user uid}])))

(defn get-bookmark [{:keys [url email]}]
  (let [query '[:find ?url ?title ?tx
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
        (zipmap [:url :title :ts] bookmark)
        [:ts]
        #(:db/txInstant (d/entity (d/db conn) %))))
     (d/q query db url email))))


(defn add-bookmark
  "Transact bookmark and return the resulting datom"
  [{:keys [url title email] :as bookmark}]
  (do
    (transact-bookmark bookmark)
    (get-bookmark bookmark)))


(defn get-user-bookmarks
  "Find user's bookmarks"
  [email]
  (let [query '[:find ?url ?title ?tx
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
  []
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
  []
  (map
   first
   (d/q '[:find ?email
          :where
          [?e :user/email ?email]]
        (d/db conn))))


(comment

  ;; only once for test purposes
  (d/create-database (str db-uri-base "/lesezeichen"))

  (d/delete-database (str db-uri-base "/lesezeichen"))

  (init-schema "schema.edn")

  (add-user {:email "eve@topiq.es"})

  (add-user {:email "adam@topiq.es"})

  (do
   (add-bookmark {:url "https://topiq.es" :title "TOPIQ" :email "eve@topiq.es"})
   (add-bookmark {:url "https://google.com" :title "the kraken" :email "eve@topiq.es"})
   (add-bookmark {:url "https://sup.com" :title "the void 2" :email "adam@topiq.es"})
   (add-bookmark {:url "http://boo.far" :title "foobar" :email "eve@topiq.es"})
   (add-bookmark {:url "http://boo.far" :title "foobar" :email "adam@topiq.es"}))
  ;; -------


  ;; some queries
  (-> (get-user-bookmarks "eve@topiq.es")
      aprint)

  (let [users (get-all-users)]
    (->> users
         (map get-user-bookmarks)
         (zipmap users)
         aprint))


  )

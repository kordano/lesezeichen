(ns lesezeichen.db
  (:require [datomic.api :as d]
            [clojure.java.io :as io]
            [clj-time.core :as t]
            [aprint.core :refer [aprint]]
            [lesezeichen.io :refer [transact-all]]))


(def db-uri-base "datomic:mem://")


(defn- scratch-conn
  "Create a connection to an anonymous, in-memory database."
  []
  (let [uri (str db-uri-base (d/squuid))]
    (d/delete-database uri)
    (d/create-database uri)
    (d/connect uri)))


(def conn (scratch-conn))


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


(defn add-bookmark [{:keys [url title email] :as bookmark}]
  (do
    (transact-bookmark bookmark)
    (get-bookmark bookmark)))


(defn get-user-bookmarks [email]
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


(defn get-all-bookmarks []
  (map
   #(zipmap [:url :title :user] %)
   (d/q '[:find ?url ?title ?email
          :where
          [?bm :bookmark/url ?url]
          [?bm :bookmark/title ?title]
          [?bm :bookmark/user ?uid]
          [?uid :user/email ?email]]
        (d/db conn))))

(defn get-all-users []
  (map
   first
   (d/q '[:find ?email
          :where
          [?e :user/email ?email]]
        (d/db conn))))


(comment

  (init-schema "schema.edn")

  (add-user {:email "eve@topiq.es"})

  (add-user {:email "adam@topiq.es"})

  (:db/txInstant (d/entity (d/db conn) (get-user-id "adam@topiq.es")))

  (-> (get-user-bookmarks "eve@topiq.es")
      first
      :ts)

  (get-user-bookmarks "adam@topiq.es")

  (let [users (get-all-users)]
    (->> users
         (map get-user-bookmarks)
         (zipmap users)))

  (add-bookmark {:url "https://topiq.es" :title "TOPIQ" :email "eve@topiq.es"})

  (add-bookmark {:url "https://google.com" :title "the kraken" :email "eve@topiq.es"})

  (add-bookmark {:url "https://sup.com" :title "the void 2" :email "adam@topiq.es"})

  (add-bookmark {:url "http://boo.far" :title "foobar" :email "eve@topiq.es"})

  (add-bookmark {:url "http://boo.far" :title "foobar" :email "adam@topiq.es"})

  )

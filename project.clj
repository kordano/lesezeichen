(defproject lesezeichen "0.1.0-SNAPSHOT"

  :description "Simple bookmarking application"

  :url "http://example.com/FIXME"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "1.7.107"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/core.memoize "0.5.7"] ;; needed for figwheel

                 [http-kit "2.1.19"]
                 [ring "1.4.0"]
                 [com.cemerick/friend "0.2.1"]
                 [enlive "1.1.6"]
                 [compojure "1.4.0"]
                 [com.draines/postal "1.11.3"]
                 [com.taoensso/timbre "4.1.1"]

                 [com.datomic/datomic-free "0.9.5206"]

                 [prismatic/dommy "1.1.0"]
                 [om "0.7.3"]
                 [kioo "0.4.0"]
                 [jarohen/chord "0.6.0"]
                 [figwheel "0.3.7"]
                 [clj-time "0.11.0"]
                 [aprint "0.1.3"]]

  :source-paths ["src/cljs" "src/clj"]

  :min-lein-version "2.0.0"

  :main lesezeichen.core

  :plugins [[lein-cljsbuild "1.0.3"]
            [lein-figwheel "0.1.4-SNAPSHOT"]]

  :figwheel {:http-server-root "public"
             :port 3449
             :css-dirs ["resources/public/css"]}

  :cljsbuild
  {:builds
   {:client-dev
    {:source-paths ["src/cljs/lesezeichen/client"]
     :compiler {:output-to "resources/public/js/compiled/client/main.js"
                :output-dir "resources/public/js/compiled/client/out"
                :optimizations :none
                :source-map true}}
    :client-prod
    {:source-paths ["src/cljs/lesezeichen/client"]
     :compiler {:output-to "resources/public/js/client.js"
                :optimizations :simple}}
    :auth-dev
    {:source-paths ["src/cljs/lesezeichen/auth"]
     :compiler {:output-to "resources/public/js/compiled/auth/main.js"
                :output-dir "resources/public/js/compiled/auth/out"
                :optimizations :none
                :source-map true}}
    :auth-prod
    {:source-paths ["src/cljs/lesezeichen/auth"]
     :compiler {:output-to "resources/public/js/auth.js"
                :optimizations :simple}}}}

  )

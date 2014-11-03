(defproject lesezeichen "0.1.0-SNAPSHOT"

  :description "A simple bookmark app"

  :url "http://example.com/FIXME"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2371"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/core.memoize "0.5.6"]

                 [http-kit "2.1.19"]
                 [ring "1.3.1"]
                 [com.cemerick/friend "0.2.1"]
                 [enlive "1.1.5"]
                 [compojure "1.2.1"]
                 [com.draines/postal "1.11.1"] [com.taoensso/timbre "3.3.1"]

                 [com.datomic/datomic-free "0.9.4899"]

                 [prismatic/dommy "1.0.0"]
                 [om "0.7.3"]
                 [kioo "0.4.0"]
                 [jarohen/chord "0.4.2"]
                 [figwheel "0.1.4-SNAPSHOT"]
                 [com.facebook/react "0.11.2"]
                 [facts/speech-synthesis "1.0.0"]
                 [clj-time "0.8.0"]
                 [aprint "0.1.1"]]

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
   {:main-dev
    {:source-paths ["src/cljs/lesezeichen/client"]
     :compiler {:output-to "resources/public/js/compiled/core/main.js"
                :output-dir "resources/public/js/compiled/core/out"
                :optimizations :none
                :source-map true}}
    :main-prod
    {:source-paths ["src/cljs/lesezeichen/client"]
     :compiler {:output-to "resources/public/js/main.js"
                :optimizations :simple}}
    :auth-dev
    {:source-paths ["src/cljs/lesezeichen/auth"]
     :compiler {:output-to "resources/public/js/compiled/auth/auth.js"
                :output-dir "resources/public/js/compiled/auth/out"
                :optimizations :none
                :source-map true}}
    :auth-prod
    {:source-paths ["src/cljs/lesezeichen/auth"]
     :compiler {:output-to "resources/public/js/auth.js"
                :optimizations :simple}}}}

  )

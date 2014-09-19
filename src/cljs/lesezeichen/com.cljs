(ns lesezeichen.com
  (:require [goog.net.XhrIo :as xhr]
            [goog.net.WebSocket]
            [goog.net.WebSocket.EventType :as event-type]
            [goog.events :as events]
            [cljs.reader :refer [read-string]]
            [cljs.core.async :as async :refer [<! >! chan close! put!]])
  (:require-macros [cljs.core.async.macros :refer [go alt! go-loop]]))


(defn connect!
  "Connects with websocket"
  ([uri] (connect! uri {}))
  ([uri {:keys [in out] :or {in chan out chan}}]
      (let [on-connect (chan)
            in (in)
            out (out)
            websocket (goog.net.WebSocket.)]
        (.log js/console (str "establishing websocket: " uri))
        (doto websocket
          (events/listen event-type/MESSAGE
                         (fn [m]
                              (let [data (read-string (.-message m))]
                                (put! out data))))
          (events/listen event-type/OPENED
                         (fn []
                           (close! on-connect)
                           (.log js/console "channel opened")
                           (go-loop []
                                    (let [data (<! in)]
                                      (if-not (nil? data)
                                        (do (.send websocket (pr-str data))
                                            (recur))
                                        (do (close! out)
                                            (.close websocket)))))))
          (events/listen event-type/CLOSED
                         (fn []
                            (.log js/console "channel closed")
                            (close! in)
                            (close! out)))
          (events/listen event-type/ERROR (fn [e] (.log js/console (str "ERROR:" (.-message e)))))
          (.open uri))
        (go
          (<! on-connect)
          {:uri uri :websocket websocket :in in :out out}))))

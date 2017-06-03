(require '[cljs.nodejs :as nodejs])
(def socket-io (nodejs/require "socket.io"))
(-> (.listen socket-io 3000)
    (.on "connection"
         (fn [socket]
           (.on socket "event"
                (fn [data]
                  (println "event!")
                  (println data)
                  (.emit socket "event" data))))))

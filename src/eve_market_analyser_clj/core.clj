(ns eve-market-analyser-clj.core
  (:gen-class)
  (:require [zeromq.zmq :as zmq]
            [cheshire.core :as chesh])
  (:import java.util.zip.Inflater
           java.nio.charset.Charset))

(defn decompress [byte-arr]
  (let [inflater (Inflater.)
        out-byte-arr (byte-array (-> (count byte-arr) (* 16)))]
    (.setInput inflater byte-arr)
    (let [length (.inflate inflater out-byte-arr)]
      (byte-array length out-byte-arr))))

(defn to-string [^bytes x]
  (String. x (Charset/forName "UTF-8")))

(defn feed->region-item [feed-item]
  (let [rows (:rowsets feed-item)]
    (map (fn [row]
           {:generatedTime (:generatedAt row)})
         rows)))

(defn -main []
  (let [context (zmq/context 1)]
    (println "Connecting to EMDR server…")
    (with-open [subscriber (doto (zmq/socket context :sub)
                             (zmq/connect "tcp://relay-eu-germany-1.eve-emdr.com:8050")
                             (zmq/set-receive-timeout 10000)
                             (zmq/subscribe ""))]
      (dotimes [i 1]
        (println "Receiving item...")
        (let [bytes (zmq/receive subscriber)]
          (println "Received :" (-> bytes decompress to-string )))))))

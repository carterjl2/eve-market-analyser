(ns eve-market-analyser.db
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [clojure.tools.logging :as log]
            [eve-market-analyser.world :as world]
            [clj-time.coerce]
            ;; Enable joda integration
            [monger.joda-time])
  (:import [com.mongodb
            BasicDBObject MongoOptions ServerAddress WriteConcern]))

;; Use fastest write concern so that pi can keep up
;; TODO: Use bulk writes so that a safer setting can be used
(mg/set-default-write-concern! WriteConcern/UNACKNOWLEDGED)

(defonce ^:private conn
  (delay
   (let [^ServerAddress server
         (mg/server-address "127.0.0.1" 27017)
         ^MongoOptions opts
         (mg/mongo-options {:connections-per-host 2})]
     (mg/connect server opts))))

(defn- get-db [] (mg/get-db @conn "eve"))

(def ^:private marketItemColl "marketItem")

(defn- orderItem->doc [orderItem]
  (doto (BasicDBObject.)
    (.put "price" (:price orderItem))
    (.put "quantity" (:quantity orderItem))))

(defn- marketItem->doc [marketItem]
  (doto (BasicDBObject.)
    (.put "typeId" (:typeId marketItem))
    (.put "itemName" (:itemName marketItem))
    (.put "regionId" (:regionId marketItem))
    (.put "regionName" (:regionName marketItem))
    (.put "sellingPrice" (:sellingPrice marketItem))
    (.put "buyingPrice" (:buyingPrice marketItem))
    (.put "generatedTime" (clj-time.coerce/to-date (:generatedTime marketItem)))
    (.put "sellOrders" (map orderItem->doc (:sellOrders marketItem)))
    (.put "buyOrders" (map orderItem->doc (:buyOrders marketItem)))))

(defn insert-items [items]
  (log/debug "Inserting items into DB...")
  (doseq [item items]
    (let [doc (marketItem->doc item)
          updateQuery {"typeId" (:typeId item)
                       "regionId" (:regionId item)
                       "generatedTime" {"$lt" (:generatedTime item)}}]
      (try
        (mc/update (get-db) marketItemColl updateQuery doc {:upsert true})
        (catch com.mongodb.DuplicateKeyException e
          (log/debug "Item older than current; ignoring")))))
  (log/debug "Inserted items into DB"))

(def ^:private hub-ordering
  (let [names world/trade-hub-region-names]
    (->>
     (map #(vector % (.indexOf names %)) names)
     flatten
     (apply hash-map))))

(defn find-hub-prices-for-item-name
  [itemName & fields]
  (let [results
         (mc/find-maps (get-db) marketItemColl
                       {:itemName itemName
                        :regionName {$in world/trade-hub-region-names}}
                       (if fields fields {}))]
    ;; If region name was included in the search, then sort according to
    ;; the trade hub priority order
    (if (some #{:regionName} fields)
      (sort-by #(hub-ordering (:regionName %)) results)
      results)))

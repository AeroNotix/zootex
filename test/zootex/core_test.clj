(ns zootex.core-test
  (:require [clojure.test :refer :all]
            [zookeeper :as zk]
            [zootex.core :refer :all]))

(defn only-one? [f coll]
  (= (count (get (group-by f coll) true)) 1))

(deftest ensure-mutex-works
  (let [zk-host "127.0.0.1:2181"
        clients (take 10 (repeatedly #(zk/connect zk-host)))]
    (try
      (is (only-one? true? (pmap lock clients)))
      (finally
        (mapv unlock clients)))))

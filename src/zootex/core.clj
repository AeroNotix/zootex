(ns zootex.core
  (:require [zookeeper :as zk]
            [zookeeper.util :as zutil]))


(def base-zootex-path "/zootex")

(defn predecessor-of [my-node ordered-children]
  (let [child-name (subs my-node (inc (count base-zootex-path)))]
    (str base-zootex-path "/" (nth ordered-children (dec (.indexOf ordered-children child-name))))))

(defn watch-predecessor [client my-node ordered-children]
  (let [predecessor (predecessor-of my-node ordered-children)
        watch-trigger (promise)
        watch-fn (fn [{:keys [event-type path]}]
                   (when (= event-type :NodeDeleted)
                     (deliver watch-trigger true)))]
    (when (zk/exists client predecessor :watcher watch-fn)
      watch-trigger)))

(defn unlock [client]
  (zk/close client))

(defn winning-lock? [my-node all-children]
  (apply <= (cons (zutil/extract-id my-node)
              (sort (mapv zutil/extract-id all-children)))))

(declare wait-for-unlock)

(defn lock [client & {:keys [wait-for-unlock?]}]
  (when-not (zk/exists client base-zootex-path)
    (zk/create client base-zootex-path :persistent? true))
  (let [my-node (zk/create client (str base-zootex-path "/-lock") :sequential? true)
        all-children (zk/children client base-zootex-path)]
    (if (winning-lock? my-node all-children)
      true
      (when wait-for-unlock?
        (wait-for-unlock client my-node all-children)))))

(defn wait-for-unlock [client my-node all-children]
  (let [node-name (subs my-node (inc (count base-zootex-path)))
        ordered-children (zutil/sort-sequential-nodes (zk/children client base-zootex-path))
        watch-trigger (watch-predecessor client my-node ordered-children)]
    (if watch-trigger
      @watch-trigger
      (do
        (unlock client)
        (lock client)))))

(defmacro with-lock [zookeeper-location & body]
  `(let [client# (zk/connect ~zookeeper-location)]
     (try
       (lock client# {:wait-for-unlock? true})
       ~@body
       (finally
         (unlock client#)))))

(ns org.zalando.stups.mint.test-utils
  (:require [clojure.test :refer :all]
            [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.mint.storage.sql :as sql]
            [com.stuartsierra.component :as component]))


(defn track
  "Adds a tuple on call for an action."
  ([a action]
   (fn [& all-args]
     (swap! a conj {:key  action
                    :args (into [] all-args)}))))

(defmacro with-db [[db] & body]
  `(let [config# (config/load-configuration
                   (system/default-http-namespaces-and :db)
                   [sql/default-db-configuration])
         ~db (component/start (sql/map->DB {:configuration (:db config#)}))]
     (try
       ~@body
       (finally
         (component/stop ~db)))))

(defmacro same!
  [x y]
  `(is (= ~x ~y)))

(defmacro true!
  [x]
  `(same! true ~x))
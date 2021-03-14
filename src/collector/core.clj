(ns collector.core
  (:gen-class)
  (:require [clj-http.client :as client]
            [cheshire.core :refer :all]
            [iapetos.core :as prometheus]
            [iapetos.export :as export]
            [iapetos.standalone :as standalone]
            [environ.core :refer [env]]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [clojure.walk :as walk]))

(def url-base "https://api.ecobee.com")
(def api-key (env :api-key))
(def refresh (env :refresh-token))
(def server-port (Integer/parseInt (env :port)))
(def scope "openid,offline_access,smartRead")

(println api-key refresh server-port)

(def sensor-temperature
  (prometheus/gauge
   :ecobee/temperature
   {:description "sensor temperature"
    :labels [:sensor-name]}))

(def sensor-humidity
  (prometheus/gauge
   :ecobee/humidity
   {:description "sensor humidity"
    :labels [:sensor-name]}))

(def sensor-occupancy
  (prometheus/gauge
   :ecobee/occupancy
   {:description "sensor occupancy"
    :labels [:sensor-name]}))

(defonce registry
  (-> (prometheus/collector-registry)
      (prometheus/register
       sensor-temperature
       sensor-humidity
       sensor-occupancy)))

(defn get-auth-code
  []
  (let [req (format "%s/authorize?response_type=ecobeePin&client_id=%s&scope=%s"
                    url-base api-key
                    scope)
        resp-body (-> (client/get req {:accept :json})
                      :body
                      (parse-string true))]
    (spit "auth" resp-body)
    (prn resp-body)))

(defn get-tokens
  [auth-code grant-type]
  (let [req (format "%s/token" url-base)
        req-data {:form-params {:grant_type grant-type
                                :code auth-code
                                :client_id api-key}
                  :as :json}]
    (:body (client/post req req-data))))

(defn get-sensors
  [access-token]
  (let [req-params {:selection {:selectionType "registered"
                                :selectionMatch ""
                                :includeSensors true
                                :includeRuntime true}}
        uri (format "%s/1/thermostat?format=json&body=%s"
                    url-base
                    (generate-string req-params))
        resp (client/get uri {:headers {"Authorization" (format "Bearer %s"
                                                                access-token)}
                              :as :json
                              :throw-exceptions false})]
    (when (= 200 (:status resp))
      (-> resp
          (get-in [:body :thermostatList])
          (first)
          :remoteSensors))))

(defn parse-metrics [sensor]
  (let [name (sensor :name)
        metrics (->> (sensor :capability)
                     (mapcat
                      (fn [metric]
                        (let [type (metric :type)
                              value (metric :value)]
                          (case type
                            "temperature" {type (-> value
                                                    (Integer/parseInt)
                                                    (/ 10)
                                                    (float))}
                            "humidity" {type (-> value
                                                 (Integer/parseInt))}
                            "occupancy" {type (get {false 0 true 1} (= value "true"))}))))
                     (into {})
                     (walk/keywordize-keys))]
    {:name (str/lower-case name) :metrics metrics}))

(defn -main [& args]
  (let [metric-server (standalone/metrics-server registry {:port server-port})
        tokens (get-tokens refresh "refresh_token")
        refresh (get tokens :refresh_token)
        access-token (get tokens :access_token)
        sensor-data (get-sensors access-token)]
    (loop [sensor-data sensor-data
           access-token access-token]
      (if (some? sensor-data)
        (do
          (let [sensors (mapv #(parse-metrics %) sensor-data)]
            (doseq [sensor sensors]
              (let [metrics (sensor :metrics)]
                (when (:temperature metrics)
                  (prometheus/observe registry
                                      :ecobee/temperature
                                      {:sensor-name (:name sensor)}
                                      (:temperature metrics)))
                (when (:humidity metrics)
                  (prometheus/observe registry
                                      :ecobee/humidity
                                      {:sensor-name (:name sensor)}
                                      (:humidity metrics)))
                (when (:occupancy metrics)
                  (prometheus/observe registry
                                      :ecobee/occupancy
                                      {:sensor-name (:name sensor)}
                                      (:occupancy metrics))))))
          (println (export/text-format registry))
          (Thread/sleep (reduce * [1000 60 5]))
          (recur (get-sensors access-token) access-token))
        (recur sensor-data (get-tokens refresh "refresh_token"))))))

(comment
  (-main))

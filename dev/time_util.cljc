(ns time-util
  (:import
   (java.time Duration Instant Period)))

(defn now [] (Instant/now))

(defn minus [{:keys [instant days hours seconds]
              :or {days 0 hours 0 seconds 0}}]
  (let [*inst* (.minus instant (Period/ofDays days))
        *inst* (.minus *inst* (Duration/ofHours hours))
        *inst* (.minus *inst* (Duration/ofSeconds seconds))]
    *inst*))

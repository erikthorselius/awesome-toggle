(require '[clojure.java.shell :refer [sh]])
(defn concentrate [state]
      (shell/sh "hueadm" "light" "1" "white" "sat=53" "hue=33016" "bri=254" "ct=233") (assoc state :state :concentrate))
(defn off [state]
      (shell/sh "hueadm" "light" "1" "off") (assoc state :state :off))
(defn relax [state]
      (shell/sh "hueadm" "light" "1" "white" "ct=447" "hue=33016" "bri=144" "sat=53") (assoc state :state :relax))
(defn night-light [state]
      (shell/sh "hueadm" "light" "1" "red" "bri=100") (assoc state :state :night-light))
(defn to-date-time [date]
      (let [inst (. date toInstant)
            zone (java.time.ZoneId/of "Europe/Stockholm")]
           (java.time.ZonedDateTime/ofInstant inst zone)))
(defn to-date [date-time]
      (. java.util.Date from (. date-time toInstant)))
(defn save-state [state]
      (spit "/tmp/awesome-toggle.tmp"
            (-> state
                (update :last-update to-date)
                (pr-str))))
(defn last-state []
      (let [filename "/tmp/awesome-toggle.tmp"
            init-state {:state :concentrate :last-update (-> "#inst \"1970-01-01T00:00:00.000-00:00\"" (edn/read-string) (to-date-time))}]
           (try
             (-> (slurp filename)
                 (edn/read-string)
                 (update :last-update to-date-time))
             (catch Exception e
               (do (save-state init-state)
                   init-state)))))
(defn now []
      (-> (java.time.ZonedDateTime/now)
          (.withZoneSameInstant (java.time.ZoneId/of "Europe/Stockholm"))))
(defn toggle [last-state]
      (case (:state last-state)
            :off (concentrate last-state)
            :concentrate (relax last-state)
            :relax (night-light last-state)
            :night-light (off last-state)))
(defn- day-of-week []
       (-> (now)
           (.getDayOfWeek)
           (.toString)))
(defn workday? []
      (case (day-of-week)
            "SUNDAY" false
            "SATURDAY" false
            true))
(defn morning? []
      (->> (now)
           (.getHour)
           (>= 6 12)))
(defn not-today? [date-time]
      (-> (now)
          (. toLocalDate)
          (. equals (. date-time toLocalDate))
          (not)))
(defn force-concentrate?
      [state]
      (if (and
            (not-today? (:last-update state))
            (workday?))
        (assoc state :state :off)
        state))

(-> (last-state)
    (force-concentrate?)
    (toggle)
    (assoc :last-update (now))
    (save-state))
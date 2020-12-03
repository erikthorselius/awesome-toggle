(require '[clojure.java.shell :refer [sh]])
; read and write ZonedDateTime to file
(defmethod print-method java.time.ZonedDateTime [v ^java.io.Writer w]
           (.write w (format "#java/zoned[\"%s\"]" v)))

(defn parse-zoned [^java.time.ZonedDateTime v]
      (java.time.ZonedDateTime/parse v))

(defn read-zoned [v]
      (parse-zoned (first v)))

(set! *data-readers*
      (assoc *data-readers* 'java/zoned read-zoned))

(def default-config {:next-mode :concentrate :last-update #java/zoned["1970-01-01T01:00+01:00[Europe/Stockholm]"]})
(defn save-state
      ([filename]
       (save-state filename default-config))
      ([filename state]
       (spit filename (pr-str state))
       state))

(defn load-state [filename]
      (try
        (edn/read-string {:readers *data-readers*} (slurp filename))
        (catch Exception e
          (save-state filename))))

; force concentrate first time on workdays
(defn now []
      (-> (java.time.ZonedDateTime/now)
          (.withZoneSameInstant (java.time.ZoneId/of "Europe/Stockholm"))))

(defn after-work-time []
      (let [aw-time (java.time.LocalTime/of 16 00)]
           (-> (java.time.ZonedDateTime/now)
               (. with aw-time)
               (.withZoneSameInstant (java.time.ZoneId/of "Europe/Stockholm")))))

(defn- day-of-week [date-time]
       (-> date-time
           (.getDayOfWeek)
           (.toString)))

(defn workday? [date-time]
      (case (day-of-week date-time)
            "SUNDAY" false
            "SATURDAY" false
            true))
(defn spy [in]
      (println in)
      in)
(defn- timespan [from to date-time]
       (<= from (. date-time getHour) to))
(def morning? (partial timespan 6 12))
(def after-work? (partial timespan 16 23))

(defn is-before? [last-update, aw-time]
      (. last-update isBefore aw-time))

(defn not-today? [last-update, now]
      (let [last-update-l (. last-update toLocalDate)]
           (-> now
               (. toLocalDate)
               (. equals last-update-l)
               (not))))

(defn force-concentrate? [{:keys [last-update] :as state}]
      (let [now (now)]
           (if (and
                 (not-today? last-update now)
                 (workday? now))
             (assoc state :next-mode :concentrate)
             state)))

(defn force-relax? [{:keys [last-update] :as state}]
      (if (and (after-work? (now))
               (is-before? last-update (after-work-time)))
        (assoc state :next-mode :relax)
        state))

; commands
(defn concentrate []
      (shell/sh "hueadm" "light" "1" "white" "sat=53" "hue=33016" "bri=254" "ct=233"))

(defn off []
      (shell/sh "hueadm" "light" "1" "off"))

(defn relax []
      (shell/sh "hueadm" "light" "1" "white" "ct=447" "hue=33016" "bri=144" "sat=53"))

(defn night-light []
      (shell/sh "hueadm" "light" "1" "red" "bri=100"))

(defn- update-mode-to [state next-mode]
       (assoc state :next-mode next-mode))

(defn toggle [{:keys [next-mode] :as state}]
      (case next-mode
            :off (update-mode-to state :concentrate)
            :concentrate (update-mode-to state :relax)
            :relax (update-mode-to state :night-light)
            :night-light (update-mode-to state :off)))

(defn run-command [{:keys [next-mode] :as state}]
      (do (case next-mode
                :off (off)
                :concentrate (concentrate)
                :relax (relax)
                :night-light (night-light))
          state))

(defn last-update [state]
      (assoc state :last-update (now)))


(let [config-file "/tmp/awesome-toggle.tmp"]
       (->> (load-state config-file)
            (force-concentrate?)
            (force-relax?)
            (run-command)
            (toggle)
            (last-update)
            (save-state config-file)))
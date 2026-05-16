(ns garnix-cli.runs
  (:require
   [clojure.string :as str]))

(def pending-statuses #{"pending" "queued" "in_progress" "requested" "waiting"})

(def status-groups
  {"success"         #{"success"}
   "failure"         #{"failure"}
   "failed"          #{"failure"}
   "cancelled"       #{"cancelled"}
   "canceled"        #{"cancelled"}
   "completed"       #{"success" "failure" "cancelled"}
   "pending"         #{"pending"}
   "queued"          #{"pending"}
   "in_progress"     #{"pending"}
   "requested"       #{"pending"}
   "waiting"         #{"pending"}
   "action_required" #{"failure"}
   "neutral"         #{"success"}
   "skipped"         #{"success"}
   "stale"           #{"failure"}
   "startup_failure" #{"failure"}
   "timed_out"       #{"failure"}})

(defn field [m k]
  (cond
    (contains? m k) (get m k)
    (contains? m (name k)) (get m (name k))
    :else nil))

(defn count-field [m k]
  (long (or (field m k) 0)))

(defn summary-status [summary]
  (cond
    (pos? (count-field summary :pending)) "pending"
    (pos? (count-field summary :failed)) "failure"
    (pos? (count-field summary :cancelled)) "cancelled"
    :else "success"))

(defn filter-commits [commits status]
  (if (str/blank? status)
    commits
    (let [status (str/lower-case status)
          group  (get status-groups status)]
      (when-not group
        (throw (ex-info (str "Unsupported status: " status)
                        {:type   :garnix-cli.runs/unsupported-status
                         :status status})))
      (filter #(contains? group (summary-status %)) commits))))

(defn terminal? [response]
  (zero? (count-field (or (field response :summary) response) :pending)))

(defn exit-code [response]
  (let [summary (or (field response :summary) response)]
    (if (or (pos? (count-field summary :failed))
            (pos? (count-field summary :cancelled)))
      1
      0)))

(ns garnix-cli.format
  (:require
   [babashka.json :as json]
   [clojure.string :as str]))

(defn field [m k]
  (cond
    (contains? m k) (get m k)
    (contains? m (name k)) (get m (name k))
    :else nil))

(defn- summary [response]
  (or (field response :summary) {}))

(defn- builds [response]
  (vec (or (field response :builds) [])))

(defn- text [x]
  (cond
    (nil? x) ""
    (keyword? x) (name x)
    :else (str x)))

(defn- short-commit [commit]
  (subs commit 0 (min 8 (count commit))))

(def failure-statuses #{"Failed" "Failure"})

(defn failed-status? [status]
  (contains? failure-statuses (text status)))

(defn status-with-label [build]
  (let [status (text (or (field build :status) "Unknown"))
        label  (case status
                 "Success" "[OK]"
                 "Failed" "[FAIL]"
                 "Failure" "[FAIL]"
                 "Pending" "⏳"
                 "Cancelled" "[CANCELLED]"
                 "[UNKNOWN]")]
    (str label " " status)))

(defn- count-summary [s k]
  (long (or (field s k) 0)))

(defn success-rate [response]
  (let [s         (summary response)
        succeeded (count-summary s :succeeded)
        total     (+ succeeded
                     (count-summary s :failed)
                     (count-summary s :pending)
                     (count-summary s :cancelled))]
    (if (zero? total)
      100.0
      (* 100.0 (/ succeeded total)))))

(defn summary-status [s]
  (cond
    (pos? (count-summary s :pending)) "pending"
    (pos? (count-summary s :failed)) "failure"
    (pos? (count-summary s :cancelled)) "cancelled"
    :else "success"))

(defn counts-text [s]
  (str (count-summary s :succeeded) " succeeded, "
       (count-summary s :failed) " failed, "
       (count-summary s :pending) " pending, "
       (count-summary s :cancelled) " cancelled"))

(defn- rate-line [response]
  (let [rate  (success-rate response)
        label (cond
                (= 100.0 rate) "[SUCCESS]"
                (<= 80.0 rate) "[GOOD]"
                :else "[WARNING]")]
    (format "%s Success Rate: %.1f%%" label rate)))

(defn- format-human [response]
  (let [s             (summary response)
        failed-builds (filter #(failed-status? (field % :status)) (builds response))
        base-lines    [(str "# Build Summary for " (short-commit (text (field s :git_commit))))
                       ""
                       (str "**Repository:** " (field s :repo_owner) "/" (field s :repo_name))
                       (str "**Branch:** " (field s :branch))
                       (str "**Started:** " (field s :start_time))
                       ""
                       "## Summary"
                       (str "- [OK] Succeeded: " (count-summary s :succeeded))
                       (str "- [FAIL] Failed: " (count-summary s :failed))
                       (str "- ⏳ Pending: " (count-summary s :pending))
                       (str "- [CANCELLED] Cancelled: " (count-summary s :cancelled))]
        failed-lines  (when (seq failed-builds)
                        (concat ["" "[SEARCH] Failed Builds:"]
                                (mapcat (fn [build]
                                          (let [drv-path (field build :drv_path)]
                                            (cond-> [(str "  • " (field build :package)
                                                          " (" (or (field build :system) "unknown") "): "
                                                          (status-with-label build))]
                                              (field build :id) (conj (str "    Build ID: " (field build :id)))
                                              drv-path (conj (str "    Derivation: " drv-path)))))
                                        failed-builds)))]
    (str/join "\n" (concat base-lines failed-lines ["" (rate-line response)]))))

(defn- format-plain [response]
  (let [s           (summary response)
        build-lines (map (fn [build]
                           (str "  " (or (field build :id) "unknown")
                                "  " (field build :package)
                                " - " (text (field build :status))
                                " (" (or (field build :system) "unknown") ")"))
                         (builds response))]
    (str/join "\n"
              (concat
               [(str "Build Status for " (field s :git_commit))
                (str "Repository: " (field s :repo_owner) "/" (field s :repo_name))
                (str "Branch: " (field s :branch))
                (str "Started: " (field s :start_time))
                ""
                "Summary:"
                (str "  Succeeded: " (count-summary s :succeeded))
                (str "  Failed: " (count-summary s :failed))
                (str "  Pending: " (count-summary s :pending))
                (str "  Cancelled: " (count-summary s :cancelled))
                ""]
               (when (seq build-lines)
                 (concat ["Individual Builds:"] build-lines [""]))
               [(format "Success Rate: %.1f%%" (success-rate response))]))))

(defn format-response [response output-format]
  (case output-format
    :json (json/write-str response)
    :edn (pr-str response)
    :plain (format-plain response)
    :human (format-human response)
    (format-human response)))

(defn- format-log-lines [logs]
  (map (fn [entry]
         (str "[" (field entry :timestamp) "] " (field entry :log_message)))
       logs))

(defn- format-log-text [log-response build-id]
  (let [logs (or (field log-response :logs) [])]
    (if (seq logs)
      (str/join "\n"
                (concat [(str "Logs for build " (or build-id "unknown")
                              " (finished: " (field log-response :finished) "):")
                         (apply str (repeat 60 "="))]
                        (format-log-lines logs)))
      (str "No logs available" (when build-id (str " for build " build-id))))))

(defn format-logs
  ([log-response output-format]
   (format-logs log-response output-format nil))
  ([log-response output-format build-id]
   (case output-format
     :json (json/write-str log-response)
     :edn (pr-str log-response)
     (format-log-text log-response build-id))))

(defn- commit-row [commit]
  (str (short-commit (text (field commit :git_commit)))
       "  " (summary-status commit)
       "  " (or (field commit :branch) "unknown")
       "  " (field commit :start_time)
       "  " (counts-text commit)))

(defn- format-commit-list-plain [{:keys [repo commits]}]
  (str/join "\n"
            (concat [(str "Recent Garnix runs for " (:slug repo))]
                    (map commit-row commits))))

(defn format-commit-list [{:keys [commits] :as data} output-format]
  (let [data (assoc data :commits (vec commits))]
    (case output-format
      :json (json/write-str data)
      :edn (pr-str data)
      (format-commit-list-plain data))))

(defn format-watch [response mode]
  (case mode
    :compact (let [s (summary response)]
               (str (short-commit (text (field s :git_commit)))
                    " " (field s :repo_owner) "/" (field s :repo_name)
                    " " (or (field s :branch) "unknown")
                    " " (summary-status s)
                    " — " (counts-text s)))
    (format-response response mode)))

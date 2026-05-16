(ns garnix-cli.format-test
  (:require
   [babashka.json :as json]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [garnix-cli.format :as fmt]))

(def sample-response
  {:summary {:repo_owner     "owner"
             :repo_name      "repo"
             :repo_is_public true
             :git_commit     "abcdef1234567890"
             :branch         "main"
             :req_user       "alice"
             :start_time     "2026-05-16T12:00:00Z"
             :succeeded      2
             :failed         1
             :pending        1
             :cancelled      0}
   :builds  [{:id         "build-ok"
              :package    "pkg-ok"
              :system     "x86_64-linux"
              :status     "Success"
              :start_time "2026-05-16T12:00:01Z"
              :end_time   "2026-05-16T12:01:00Z"}
             {:id         "build-fail"
              :package    "pkg-fail"
              :system     nil
              :status     "Failed"
              :drv_path   "/nix/store/pkg-fail.drv"
              :start_time "2026-05-16T12:00:02Z"
              :end_time   "2026-05-16T12:02:00Z"}
             {:id      "build-pending"
              :package "pkg-pending"
              :status  "Pending"}]
   :runs    []})

(deftest success-rate-uses-summary-total-not-build-vector-count
  (is (= 50.0 (fmt/success-rate sample-response))))

(deftest status-with-label-covers-known-and-unknown-statuses
  (is (= ["[OK] Success" "[FAIL] Failed" "⏳ Pending" "[CANCELLED] Cancelled" "[UNKNOWN] Weird"]
         (mapv fmt/status-with-label
               [{:status "Success"}
                {:status "Failed"}
                {:status "Pending"}
                {:status "Cancelled"}
                {:status "Weird"}]))))

(deftest human-format-includes-summary-failures-and-success-rate
  (let [out (fmt/format-response sample-response :human)]
    (is (= [true true true true]
           [(str/includes? out "# Build Summary for abcdef12")
            (str/includes? out "**Repository:** owner/repo")
            (str/includes? out "pkg-fail (unknown): [FAIL] Failed")
            (str/includes? out "Success Rate: 50.0%")]))))

(deftest plain-format-is-stable-and-line-oriented
  (is (= "Build Status for abcdef1234567890\nRepository: owner/repo\nBranch: main\nStarted: 2026-05-16T12:00:00Z\n\nSummary:\n  Succeeded: 2\n  Failed: 1\n  Pending: 1\n  Cancelled: 0\n\nIndividual Builds:\n  pkg-ok - Success (x86_64-linux)\n  pkg-fail - Failed (unknown)\n  pkg-pending - Pending (unknown)\n\nSuccess Rate: 50.0%"
         (fmt/format-response sample-response :plain))))

(deftest json-format-round-trips-response-data
  (testing "JSON format emits machine-readable JSON"
    (is (= sample-response
           (json/read-str (fmt/format-response sample-response :json))))))

(deftest edn-format-round-trips-response-data
  (testing "EDN format emits machine-readable EDN"
    (is (= sample-response
           (edn/read-string (fmt/format-response sample-response :edn))))))

(deftest log-format-includes-build-header-finished-state-and-entries
  (is (= "Logs for build build-123 (finished: true):\n============================================================\n[2026-05-16T12:00:01Z] first line\n[2026-05-16T12:00:02Z] second line"
         (fmt/format-logs {:finished true
                           :logs     [{:timestamp   "2026-05-16T12:00:01Z"
                                       :log_message "first line"}
                                      {:timestamp   "2026-05-16T12:00:02Z"
                                       :log_message "second line"}]}
                          :human
                          "build-123"))))

(deftest log-format-supports-edn-output
  (let [log-response {:finished false :logs []}]
    (is (= log-response
           (edn/read-string (fmt/format-logs log-response :edn "build-123"))))))

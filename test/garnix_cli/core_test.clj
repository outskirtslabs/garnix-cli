(ns garnix-cli.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [garnix-cli.core :as core]))

(deftest parse-command-supports-fetch-positionals-and-options
  (is (= {:command   :fetch
          :commit-id "abc123"
          :format    :json
          :base-url  "https://api.garnix.io"
          :token     "cli-token"}
         (select-keys (core/parse-command ["--token" "cli-token"
                                           "--format" "json"
                                           "fetch"
                                           "abc123"])
                      [:command :commit-id :format :base-url :token]))))

(deftest parse-command-supports-edn-format
  (is (= {:command :fetch
          :format  :edn}
         (select-keys (core/parse-command ["--format" "edn" "fetch" "abc123"])
                      [:command :format]))))

(deftest parse-command-supports-global-output-shortcuts
  (is (= [{:command :list :format :json :repo "owner/repo" :limit 5}
          {:command :view :format :edn :repo "owner/repo" :limit 20}
          {:command :watch :format :plain :repo "owner/repo" :limit 20}]
         (mapv #(select-keys (core/parse-command %) [:command :format :repo :limit])
               [["--json" "list" "-R" "owner/repo" "--limit" "5"]
                ["--edn" "view" "-R" "owner/repo"]
                ["--plain" "watch" "-R" "owner/repo"]]))))

(deftest parse-command-keeps-auth-options-minimal
  (is (= {:token "cli-token"}
         (select-keys (core/parse-command ["--token" "cli-token" "fetch" "abc123"])
                      [:token]))))

(deftest parse-command-rejects-unsupported-token-aliases
  (is (thrown-with-msg? Exception
                        #"Unknown option"
                        (core/parse-command ["--jwt-token" "jwt" "fetch" "abc123"]))))

(deftest parse-command-supports-legacy-commit-id-flag
  (is (= {:command   :fetch
          :commit-id "abc123"}
         (select-keys (core/parse-command ["fetch" "--commit-id" "abc123"])
                      [:command :commit-id]))))

(deftest parse-command-supports-logs-build-id-and-raw-flag
  (is (= {:command  :logs
          :build-id "B5aPM2pB"
          :raw      true}
         (select-keys (core/parse-command ["logs" "B5aPM2pB" "--raw"])
                      [:command :build-id :raw]))))

(deftest parse-command-supports-list-with-repo-status-and-limit
  (is (= {:command :list
          :repo    "owner/repo"
          :status  "failure"
          :limit   5}
         (select-keys (core/parse-command ["list" "-R" "owner/repo" "--status" "failure" "--limit" "5"])
                      [:command :repo :status :limit]))))

(deftest parse-command-supports-view-latest-for-repo
  (is (= {:command :view
          :repo    "owner/repo"}
         (select-keys (core/parse-command ["view" "--repo" "owner/repo"])
                      [:command :repo]))))

(deftest parse-command-supports-watch-flags
  (is (= {:command     :watch
          :repo        "owner/repo"
          :compact     true
          :exit-status true
          :interval    7}
         (select-keys (core/parse-command ["watch" "-R" "owner/repo" "--compact" "--exit-status" "--interval" "7"])
                      [:command :repo :compact :exit-status :interval]))))

(deftest parse-command-rejects-missing-required-positional-values
  (testing "fetch requires a commit id"
    (is (= {:type    :garnix-cli.core/usage-error
            :message "fetch requires a commit id"}
           (try
             (core/parse-command ["fetch"])
             nil
             (catch Exception e
               {:type    (:type (ex-data e))
                :message (ex-message e)})))))
  (testing "unknown commands produce usage errors"
    (is (= {:type    :garnix-cli.core/usage-error
            :message "unknown command: nope"}
           (try
             (core/parse-command ["nope"])
             nil
             (catch Exception e
               {:type    (:type (ex-data e))
                :message (ex-message e)}))))))

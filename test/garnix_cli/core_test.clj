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

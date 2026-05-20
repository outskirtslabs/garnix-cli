(ns garnix-cli.auth-test
  (:require
   [babashka.fs :as fs]
   [clojure.test :refer [deftest is testing]]
   [garnix-cli.auth :as auth]))

(deftest dotenv-map-parses-common-env-file-syntax
  (let [dir (fs/create-temp-dir)]
    (try
      (let [env-file (fs/path dir ".env")]
        (spit (str env-file)
              (str "# ignored\n"
                   "GARNIX_API_TOKEN=from-file\n"
                   "OTHER='single quoted'\n"
                   "BLANK=\n"))
        (is (= {"GARNIX_API_TOKEN" "from-file"
                "OTHER"            "single quoted"
                "BLANK"            ""}
               (auth/dotenv-map env-file))))
      (finally
        (fs/delete-tree dir)))))

(deftest resolve-token-supports-api-token-command-and-env-fallbacks
  (testing "explicit CLI token wins"
    (is (= "cli-token"
           (auth/resolve-token {:token          "cli-token"
                                :command-runner (fn [_] "command-api")
                                :env            {"GARNIX_API_TOKEN_COMMAND" "secret-tool garnix"
                                                 "GARNIX_API_TOKEN"         "env-api"}
                                :dotenv         {"GARNIX_API_TOKEN" "file-api"}}))))
  (testing "GARNIX_API_TOKEN_COMMAND wins over static env tokens"
    (is (= "command-api"
           (auth/resolve-token {:command-runner (fn [command]
                                                  (is (= "secret-tool garnix" command))
                                                  "command-api\n")
                                :env            {"GARNIX_API_TOKEN_COMMAND" "secret-tool garnix"
                                                 "GARNIX_API_TOKEN"         "env-api"}
                                :dotenv         {"GARNIX_API_TOKEN" "file-api"}}))))
  (testing "GARNIX_API_TOKEN is used when no command is configured"
    (is (= "env-api"
           (auth/resolve-token {:env    {"GARNIX_API_TOKEN" "env-api"}
                                :dotenv {}}))))
  (testing "dotenv is used when process env has no GARNIX_API_TOKEN"
    (is (= "file-api"
           (auth/resolve-token {:env    {}
                                :dotenv {"GARNIX_API_TOKEN" "file-api"}})))))

(deftest resolve-github-username-uses-env-dotenv-and-gh-status
  (testing "GARNIX_GITHUB_USERNAME wins"
    (is (= "Ramblurr"
           (auth/resolve-github-username {:env            {"GARNIX_GITHUB_USERNAME" "Ramblurr"}
                                          :dotenv         {"GARNIX_GITHUB_USERNAME" "DotenvUser"}
                                          :gh-username-fn (fn [] "GhUser")}))))
  (testing "dotenv fallback"
    (is (= "DotenvUser"
           (auth/resolve-github-username {:env            {}
                                          :dotenv         {"GARNIX_GITHUB_USERNAME" "DotenvUser"}
                                          :gh-username-fn (fn [] "GhUser")}))))
  (testing "gh auth status fallback"
    (is (= "GhUser"
           (auth/resolve-github-username {:env            {}
                                          :dotenv         {}
                                          :gh-username-fn (fn [] "GhUser")})))))

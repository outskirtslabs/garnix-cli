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

(deftest resolve-token-only-supports-garnix-api-token
  (testing "explicit CLI token wins"
    (is (= "cli-token"
           (auth/resolve-token {:token  "cli-token"
                                :env    {"GARNIX_API_TOKEN" "env-api"}
                                :dotenv {"GARNIX_API_TOKEN" "file-api"}}))))
  (testing "GARNIX_API_TOKEN is the supported env var"
    (is (= "env-api"
           (auth/resolve-token {:env {"GARNIX_API_TOKEN" "env-api"}}))))
  (testing "dotenv is used when process env has no GARNIX_API_TOKEN"
    (is (= "file-api"
           (auth/resolve-token {:env    {}
                                :dotenv {"GARNIX_API_TOKEN" "file-api"}})))))

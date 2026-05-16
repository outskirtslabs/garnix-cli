(ns garnix-cli.repo-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [garnix-cli.repo :as repo]))

(deftest parse-repo-accepts-owner-repo-and-host-owner-repo
  (is (= {:owner "outskirtslabs"
          :repo  "garnix-cli"
          :slug  "outskirtslabs/garnix-cli"}
         (repo/parse-repo "outskirtslabs/garnix-cli")))
  (is (= {:owner "outskirtslabs"
          :repo  "garnix-cli"
          :slug  "outskirtslabs/garnix-cli"}
         (repo/parse-repo "github.com/outskirtslabs/garnix-cli"))))

(deftest remote-url->repo-parses-common-git-remote-forms
  (testing "ssh remote"
    (is (= {:owner "outskirtslabs"
            :repo  "garnix-cli"
            :slug  "outskirtslabs/garnix-cli"}
           (repo/remote-url->repo "git@github.com:outskirtslabs/garnix-cli.git"))))
  (testing "https remote"
    (is (= {:owner "outskirtslabs"
            :repo  "garnix-cli"
            :slug  "outskirtslabs/garnix-cli"}
           (repo/remote-url->repo "https://github.com/outskirtslabs/garnix-cli.git")))))

(deftest parse-repo-rejects-invalid-repo-slugs
  (is (thrown-with-msg? Exception
                        #"OWNER/REPO"
                        (repo/parse-repo "not-enough"))))

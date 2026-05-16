(ns garnix-cli.runs-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [garnix-cli.runs :as runs]))

(def commits
  [{:git_commit "success" :succeeded 2 :failed 0 :pending 0 :cancelled 0}
   {:git_commit "failure" :succeeded 1 :failed 1 :pending 0 :cancelled 0}
   {:git_commit "pending" :succeeded 1 :failed 0 :pending 1 :cancelled 0}
   {:git_commit "cancelled" :succeeded 1 :failed 0 :pending 0 :cancelled 1}])

(deftest filter-commits-supports-gh-like-status-values
  (is (= ["success"]
         (mapv :git_commit (runs/filter-commits commits "success"))))
  (is (= ["failure"]
         (mapv :git_commit (runs/filter-commits commits "failure"))))
  (is (= ["pending"]
         (mapv :git_commit (runs/filter-commits commits "in_progress"))))
  (is (= ["success" "failure" "cancelled"]
         (mapv :git_commit (runs/filter-commits commits "completed")))))

(deftest terminal-and-exit-code-follow-garnix-summary
  (testing "pending builds are not terminal"
    (is (= {:terminal? false
            :exit-code 0}
           {:terminal? (runs/terminal? {:summary {:pending 1}})
            :exit-code (runs/exit-code {:summary {:pending 1}})})))
  (testing "failed terminal builds return non-zero when requested"
    (is (= {:terminal? true
            :exit-code 1}
           {:terminal? (runs/terminal? {:summary {:pending 0 :failed 1}})
            :exit-code (runs/exit-code {:summary {:pending 0 :failed 1}})}))))

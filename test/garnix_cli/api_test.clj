(ns garnix-cli.api-test
  (:require
   [babashka.json :as json]
   [clojure.test :refer [deftest is testing]]
   [garnix-cli.api :as api]))

(defn echo-json-request [req]
  {:status 200
   :body   (json/write-str
            {:seen {:method        (name (:method req))
                    :uri           (:uri req)
                    :authorization (get-in req [:headers "Authorization"])
                    :accept        (get-in req [:headers "Accept"])
                    :basic-auth    (:basic-auth req)}})})

(deftest fetch-commit-uses-documented-commits-endpoint
  (testing "uses api.garnix.io /commits/:sha with bearer auth"
    (is (= {:seen {:method        "get"
                   :uri           "https://example.test/commits/abc123"
                   :authorization "Bearer token-123"
                   :accept        "application/json"
                   :basic-auth    nil}}
           (api/fetch-commit! {:base-url   "https://example.test/"
                               :token      "token-123"
                               :request-fn echo-json-request}
                              "abc123")))))

(deftest fetch-repo-commits-uses-repo-commits-endpoint
  (is (= {:seen {:method        "get"
                 :uri           "https://example.test/commits/repo/owner/repo"
                 :authorization "Bearer token-123"
                 :accept        "application/json"
                 :basic-auth    nil}}
         (api/fetch-repo-commits! {:base-url   "https://example.test"
                                   :token      "token-123"
                                   :request-fn echo-json-request}
                                  {:owner "owner" :repo "repo"}))))

(deftest fetch-build-logs-uses-documented-build-log-endpoints
  (testing "structured logs endpoint"
    (is (= {:seen {:method        "get"
                   :uri           "https://example.test/build/B5aPM2pB/logs"
                   :authorization nil
                   :accept        "application/json"
                   :basic-auth    nil}}
           (api/fetch-build-logs! {:base-url   "https://example.test"
                                   :request-fn echo-json-request}
                                  "B5aPM2pB"))))
  (testing "raw logs endpoint returns unparsed text"
    (let [request-fn (fn [req]
                       {:status 200
                        :body   (str (name (:method req)) " " (:uri req))})]
      (is (= "get https://example.test/build/B5aPM2pB/logs/raw"
             (api/fetch-build-logs-raw! {:base-url   "https://example.test"
                                         :request-fn request-fn}
                                        "B5aPM2pB"))))))

(deftest http-errors-include-status-and-response-body
  (is (= {:type   :garnix-cli.api/http-error
          :status 404
          :body   "missing"}
         (try
           (api/fetch-commit! {:base-url   "https://example.test"
                               :request-fn (fn [_] {:status 404 :body "missing"})}
                              "missing-commit")
           nil
           (catch Exception e
             (select-keys (ex-data e) [:type :status :body]))))))
(ns garnix-cli.api
  (:require
   [babashka.http-client :as http]
   [cheshire.core :as json]
   [clojure.string :as str]))

(def default-base-url "https://api.garnix.io")

(defn- trim-trailing-slashes [s]
  (str/replace (or s default-base-url) #"/+$" ""))

(defn url [base-url path]
  (str (trim-trailing-slashes base-url) path))

(defn- success-status? [status]
  (<= 200 (long status) 299))

(defn- parse-json-body [body]
  (when-not (str/blank? body)
    (json/parse-string body true)))

(defn request!
  ([client method path]
   (request! client method path {}))
  ([{:keys [base-url token request-fn]
     :or   {base-url   default-base-url
            request-fn http/request}}
    method
    path
    opts]
   (let [default-headers (cond-> {"Accept" "application/json"}
                           (not (str/blank? token))
                           (assoc "Authorization" (str "Bearer " token)))
         headers         (merge default-headers (:headers opts))
         req             (merge {:method  method
                                 :uri     (url base-url path)
                                 :headers headers
                                 :throw   false}
                                (dissoc opts :headers))
         resp            (request-fn req)
         status          (:status resp)]
     (if (success-status? status)
       resp
       (throw (ex-info (str "Garnix API request failed with HTTP " status)
                       {:type   :garnix-cli.api/http-error
                        :status status
                        :body   (:body resp)
                        :uri    (:uri req)}))))))

(defn fetch-commit! [client commit-id]
  (-> (request! client :get (str "/commits/" commit-id))
      :body
      parse-json-body))

(defn fetch-repo-commits! [client {:keys [owner repo]}]
  (-> (request! client :get (str "/commits/repo/" owner "/" repo))
      :body
      parse-json-body))

(defn fetch-build-logs! [client build-id]
  (-> (request! client :get (str "/build/" build-id "/logs"))
      :body
      parse-json-body))

(defn fetch-build-logs-raw! [client build-id]
  (:body (request! client :get (str "/build/" build-id "/logs/raw")
                   {:headers {"Accept" "text/plain"}})))

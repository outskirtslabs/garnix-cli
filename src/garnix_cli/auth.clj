(ns garnix-cli.auth
  (:require
   [babashka.fs :as fs]
   [babashka.process :as p]
   [clojure.string :as str]))

(def token-env-var "GARNIX_API_TOKEN")
(def token-command-env-var "GARNIX_API_TOKEN_COMMAND")
(def github-username-env-var "GARNIX_GITHUB_USERNAME")

(defn- non-blank [s]
  (when-not (str/blank? s)
    s))

(defn- strip-matching-quotes [s]
  (let [s (str/trim (or s ""))]
    (if (and (>= (count s) 2)
             (or (and (= \" (first s)) (= \" (last s)))
                 (and (= \' (first s)) (= \' (last s)))))
      (subs s 1 (dec (count s)))
      s)))

(defn- parse-dotenv-line [line]
  (let [line (str/trim line)
        line (if (str/starts-with? line "export ")
               (str/trim (subs line 7))
               line)]
    (when (and (not (str/blank? line))
               (not (str/starts-with? line "#")))
      (let [[k v] (str/split line #"=" 2)]
        (when (and k v (re-matches #"[A-Za-z_][A-Za-z0-9_]*" (str/trim k)))
          [(str/trim k) (strip-matching-quotes v)])))))

(defn dotenv-map
  ([]
   (dotenv-map ".env"))
  ([path]
   (if (fs/regular-file? path)
     (->> (fs/read-all-lines path)
          (keep parse-dotenv-line)
          (into {}))
     {})))

(defn- env-and-dotenv [opts]
  {:env    (if (contains? opts :env) (:env opts) (System/getenv))
   :dotenv (if (contains? opts :dotenv)
             (:dotenv opts)
             (dotenv-map (or (:dotenv-path opts) ".env")))})

(defn- run-token-command [opts command]
  (let [out (if-let [runner (:command-runner opts)]
              (runner command)
              (let [{:keys [exit out err]} (p/shell {:out :string :err :string :continue true} command)]
                (when-not (zero? exit)
                  (throw (ex-info (str "GARNIX_API_TOKEN_COMMAND failed: " (str/trim (or err "")))
                                  {:type    :garnix-cli.auth/token-command-failed
                                   :command command
                                   :exit    exit
                                   :err     err})))
                out))]
    (non-blank (str/trim (or out "")))))

(defn resolve-token [opts]
  (let [{:keys [env dotenv]} (env-and-dotenv opts)
        explicit             (non-blank (:token opts))
        command              (or (non-blank (get env token-command-env-var))
                                 (non-blank (get dotenv token-command-env-var)))]
    (or explicit
        (some->> command (run-token-command opts))
        (non-blank (get env token-env-var))
        (non-blank (get dotenv token-env-var)))))

(defn- gh-username []
  (try
    (let [{:keys [exit out]} (p/sh {:out :string :err :string :continue true}
                                   "gh" "auth" "status"
                                   "--json" "hosts"
                                   "--jq" ".hosts.\"github.com\"[] | select(.active).login")]
      (when (zero? exit)
        (non-blank (str/trim out))))
    (catch Exception _
      nil)))

(defn resolve-github-username [opts]
  (let [{:keys [env dotenv]} (env-and-dotenv opts)]
    (or (non-blank (:github-username opts))
        (non-blank (get env github-username-env-var))
        (non-blank (get dotenv github-username-env-var))
        ((or (:gh-username-fn opts) gh-username)))))

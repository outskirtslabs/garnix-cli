(ns garnix-cli.auth
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]))

(def token-env-var "GARNIX_API_TOKEN")

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

(defn resolve-token [opts]
  (let [env      (if (contains? opts :env) (:env opts) (System/getenv))
        dotenv   (if (contains? opts :dotenv)
                   (:dotenv opts)
                   (dotenv-map (or (:dotenv-path opts) ".env")))
        explicit (non-blank (:token opts))]
    (or explicit
        (non-blank (get env token-env-var))
        (non-blank (get dotenv token-env-var)))))

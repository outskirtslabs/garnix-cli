(ns garnix-cli.core
  (:require
   [babashka.cli :as cli]
   [clojure.string :as str]
   [garnix-cli.api :as api]
   [garnix-cli.auth :as auth]
   [garnix-cli.format :as fmt]
   [garnix-cli.repo :as repo]
   [garnix-cli.runs :as runs])
  (:gen-class))

(def cli-config
  {:spec      {:token       {:desc "Bearer token (defaults to GARNIX_API_TOKEN)"
                             :ref  "<token>"}
               :base-url    {:desc "Garnix API base URL"
                             :ref  "<url>"}
               :format      {:desc     "Output format: human, plain, json, edn"
                             :ref      "<format>"
                             :coerce   :keyword
                             :validate #{:human :plain :json :edn}}
               :json        {:desc   "Output JSON"
                             :coerce :boolean}
               :edn         {:desc   "Output EDN"
                             :coerce :boolean}
               :plain       {:desc   "Output plain text"
                             :coerce :boolean}
               :commit-id   {:desc "Git commit SHA"
                             :ref  "<sha>"}
               :build-id    {:desc "Garnix build id"
                             :ref  "<id>"}
               :repo        {:desc "Select another repository using OWNER/REPO"
                             :ref  "<owner/repo>"}
               :status      {:desc "Filter runs by status"
                             :ref  "<status>"}
               :limit       {:desc   "Maximum number of runs to fetch"
                             :ref    "<n>"
                             :coerce :long}
               :compact     {:desc   "Show only compact watch output"
                             :coerce :boolean}
               :exit-status {:desc   "Exit with non-zero status if the watched run fails"
                             :coerce :boolean}
               :interval    {:desc   "Refresh interval in seconds"
                             :ref    "<seconds>"
                             :coerce :long}
               :raw         {:desc   "Print raw build logs"
                             :coerce :boolean}
               :help        {:desc   "Show help"
                             :alias  :h
                             :coerce :boolean}}
   :exec-args {:format   :human
               :base-url api/default-base-url
               :limit    20
               :interval 3}
   :alias     {:R :repo
               :L :limit
               :s :status
               :i :interval}
   :restrict  true})

(defn usage-error [message]
  (throw (ex-info message {:type :garnix-cli.core/usage-error})))

(def commands #{"help" "fetch" "list" "view" "watch" "logs"})

(def value-options
  #{"--token" "--base-url" "--format" "--commit-id" "--build-id"
    "--repo" "-R" "--status" "-s" "--limit" "-L" "--interval" "-i"})

(defn- positional-or-option [opts positional option-key]
  (or (get opts option-key) (first positional)))

(defn- find-command-index [argv]
  (loop [i 0]
    (when (< i (count argv))
      (let [arg (nth argv i)]
        (cond
          (contains? commands arg) i
          (contains? value-options arg) (recur (+ i 2))
          (and (str/starts-with? arg "--") (str/includes? arg "=")) (recur (inc i))
          :else (recur (inc i)))))))

(defn- normalize-argv [argv]
  (let [argv (vec argv)
        idx  (find-command-index argv)]
    (if (and idx (pos? idx))
      (vec (concat [(nth argv idx)]
                   (subvec argv (inc idx))
                   (subvec argv 0 idx)))
      argv)))

(defn- normalize-format [opts]
  (let [format (cond
                 (:json opts) :json
                 (:edn opts) :edn
                 (:plain opts) :plain
                 :else (:format opts))]
    (-> opts
        (assoc :format (if (keyword? format) format (keyword format)))
        (dissoc :json :edn :plain))))

(defn parse-command [argv]
  (let [argv                   (normalize-argv argv)
        {:keys [opts args]}    (cli/parse-args argv cli-config)
        [command & positional] args
        opts                   (normalize-format opts)]
    (case command
      nil (assoc opts :command (if (:help opts) :help :help))
      "help" (assoc opts :command :help)
      "fetch" (let [commit-id (positional-or-option opts positional :commit-id)]
                (when (str/blank? commit-id)
                  (usage-error "fetch requires a commit id"))
                (assoc opts :command :fetch :commit-id commit-id))
      "list" (assoc opts :command :list)
      "view" (assoc opts :command :view :commit-id (positional-or-option opts positional :commit-id))
      "watch" (assoc opts :command :watch :commit-id (positional-or-option opts positional :commit-id))
      "logs" (let [build-id (positional-or-option opts positional :build-id)]
               (when (str/blank? build-id)
                 (usage-error "logs requires a build id"))
               (assoc opts :command :logs :build-id build-id))
      (usage-error (str "unknown command: " command)))))

(defn help-text []
  (str/join
   "\n"
   ["garnix-cli - inspect Garnix build status"
    ""
    "Usage:"
    "  garnix-cli list [-R OWNER/REPO] [--status STATUS] [--limit N]"
    "  garnix-cli view [commit-sha] [-R OWNER/REPO] [--format human|plain|json|edn]"
    "  garnix-cli watch [commit-sha] [-R OWNER/REPO] [--compact] [--exit-status] [--interval N]"
    "  garnix-cli fetch <commit-sha> [--format human|plain|json|edn]"
    "  garnix-cli logs <build-id> [--raw] [--format human|json|edn]"
    ""
    "Options:"
    (cli/format-opts cli-config)]))

(defn- client [opts]
  {:base-url (:base-url opts)
   :token    (auth/resolve-token opts)})

(defn- repo-commits! [parsed]
  (let [repo    (repo/resolve-repo (:repo parsed))
        commits (:commits (api/fetch-repo-commits! (client parsed) repo))]
    {:repo    repo
     :commits commits}))

(defn- latest-commit-id! [parsed]
  (let [{:keys [repo commits]} (repo-commits! parsed)]
    (or (some-> commits first :git_commit)
        (usage-error (str "No Garnix runs found for " (:slug repo))))))

(defn- resolve-commit-id! [parsed]
  (or (:commit-id parsed)
      (latest-commit-id! parsed)))

(defn- fetch-target! [parsed]
  (api/fetch-commit! (client parsed) (resolve-commit-id! parsed)))

(defn- list-runs! [parsed]
  (let [{:keys [repo commits]} (repo-commits! parsed)
        commits                (->> (runs/filter-commits commits (:status parsed))
                                    (take (:limit parsed))
                                    vec)
        data                   {:repo repo :commits commits}]
    (println (fmt/format-commit-list data (:format parsed)))
    data))

(defn- watch! [parsed]
  (loop []
    (let [response (fetch-target! parsed)
          mode     (if (:compact parsed) :compact (:format parsed))]
      (println (fmt/format-watch response mode))
      (flush)
      (if (runs/terminal? response)
        {:response  response
         :exit-code (if (:exit-status parsed) (runs/exit-code response) 0)}
        (do
          (Thread/sleep (* 1000 (:interval parsed)))
          (recur))))))

(defn execute! [parsed]
  (case (:command parsed)
    :help (do (println (help-text)) nil)
    :list (list-runs! parsed)
    :view (let [response (fetch-target! parsed)]
            (println (fmt/format-response response (:format parsed)))
            response)
    :watch (watch! parsed)
    :fetch (let [response (api/fetch-commit! (client parsed) (:commit-id parsed))]
             (println (fmt/format-response response (:format parsed)))
             response)
    :logs (if (:raw parsed)
            (let [logs (api/fetch-build-logs-raw! (client parsed) (:build-id parsed))]
              (println logs)
              logs)
            (let [logs (api/fetch-build-logs! (client parsed) (:build-id parsed))]
              (println (fmt/format-logs logs (:format parsed) (:build-id parsed)))
              logs))))

(defn -main [& args]
  (try
    (let [result (execute! (parse-command args))]
      (when (and (map? result) (pos? (or (:exit-code result) 0)))
        (System/exit (:exit-code result))))
    (catch Exception e
      (binding [*out* *err*]
        (println (ex-message e)))
      (System/exit 1))))

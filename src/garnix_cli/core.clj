(ns garnix-cli.core
  (:require
   [babashka.cli :as cli]
   [clojure.string :as str]
   [garnix-cli.api :as api]
   [garnix-cli.auth :as auth]
   [garnix-cli.format :as fmt])
  (:gen-class))

(def cli-config
  {:spec      {:token     {:desc "Bearer token (defaults to GARNIX_API_TOKEN)"
                           :ref  "<token>"}
               :base-url  {:desc "Garnix API base URL"
                           :ref  "<url>"}
               :format    {:desc     "Output format: human, plain, json, edn"
                           :ref      "<format>"
                           :coerce   :keyword
                           :validate #{:human :plain :json :edn}}
               :commit-id {:desc "Git commit SHA"
                           :ref  "<sha>"}
               :build-id  {:desc "Garnix build id"
                           :ref  "<id>"}
               :raw       {:desc   "Print raw build logs"
                           :coerce :boolean}
               :help      {:desc   "Show help"
                           :alias  :h
                           :coerce :boolean}}
   :exec-args {:format   :human
               :base-url api/default-base-url}
   :restrict  true})

(defn usage-error [message]
  (throw (ex-info message {:type :garnix-cli.core/usage-error})))

(defn- positional-or-option [opts positional option-key]
  (or (get opts option-key) (first positional)))

(defn parse-command [argv]
  (let [{:keys [opts args]}    (cli/parse-args argv cli-config)
        [command & positional] args
        opts                   (update opts :format #(if (keyword? %) % (keyword %)))]
    (case command
      nil (assoc opts :command (if (:help opts) :help :help))
      "help" (assoc opts :command :help)
      "fetch" (let [commit-id (positional-or-option opts positional :commit-id)]
                (when (str/blank? commit-id)
                  (usage-error "fetch requires a commit id"))
                (assoc opts :command :fetch :commit-id commit-id))
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
    "  garnix-cli fetch <commit-sha> [--format human|plain|json|edn]"
    "  garnix-cli logs <build-id> [--raw] [--format human|json|edn]"
    ""
    "Options:"
    (cli/format-opts cli-config)]))

(defn- client [opts]
  {:base-url (:base-url opts)
   :token    (auth/resolve-token opts)})

(defn execute! [parsed]
  (case (:command parsed)
    :help (do (println (help-text)) nil)
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
    (execute! (parse-command args))
    (catch Exception e
      (binding [*out* *err*]
        (println (ex-message e)))
      (System/exit 1))))

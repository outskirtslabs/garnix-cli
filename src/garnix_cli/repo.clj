(ns garnix-cli.repo
  (:require
   [babashka.process :as p]
   [clojure.string :as str]))

(defn- normalize-repo-string [s]
  (-> (str/trim (or s ""))
      (str/replace #"\.git$" "")
      (str/replace #":" "/")))

(defn parse-repo [repo]
  (let [parts        (->> (str/split (normalize-repo-string repo) #"/")
                          (remove str/blank?)
                          vec)
        [owner name] (take-last 2 parts)]
    (if (and owner name)
      {:owner owner
       :repo  name
       :slug  (str owner "/" name)}
      (throw (ex-info "Repository must use OWNER/REPO format"
                      {:type :garnix-cli.repo/invalid-repo
                       :repo repo})))))

(defn remote-url->repo [remote-url]
  (parse-repo remote-url))

(defn current-repo []
  (let [{:keys [exit out err]} (p/sh {:out :string :err :string} "git" "remote" "get-url" "origin")]
    (if (zero? exit)
      (remote-url->repo out)
      (throw (ex-info "Could not determine repository. Pass -R OWNER/REPO."
                      {:type :garnix-cli.repo/missing-repo
                       :err  err})))))

(defn resolve-repo [repo]
  (if (str/blank? repo)
    (current-repo)
    (parse-repo repo)))

;; manage-topics
;;
;; it's a command line tool that manages the state of a cluster's topics
;; from an .ini file.
;;
;; TODO:
;;  + document ini format
;;  + create topics

(ns manage-topics.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [franzy.admin.topics :refer [all-topics delete-topic!]]
            [franzy.admin.zookeeper.client :as client])
  (:gen-class))

(defn- usage [options-summary]
  (->> ["Manage kafka topics"
        ""
        "Usage: manage-topics action [options]"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  check    check that the topics are right"
        "  create   create all the topics on the cluster"
        "  delete   delete all the topics on the cluster"
        "  list     list topics on the kafka cluster"
        ""
        "Have a great day!"]
       (clojure.string/join \newline)))

(def cli-options
  [["-z" "--zookeeper HOST" "the zookeeper host"
    :default "localhost:2181"]
   ["-t" "--topics-ini" "path to topics.ini file"
    :default "topics.ini"]
   ["-h" "--help"]])

(defn- exit [status msg]
  (println msg)
  (System/exit status))

(defn- check-topics
  [options]
  (println options))

(defn- get-zk [options]
  (client/make-zk-utils
   {:servers (:zookeeper options)}
   false))

(defn- list-topics
  [options]
  (with-open [zk (get-zk options)]
    (doall (map println (sort (all-topics zk))))))

(defn- delete-topics
  [options]
  (println "deleting all topics! are you sure? type 'yes'")
  (cond (not= (read-line)"yes")
        (exit 1 "bailing out"))
  (with-open [zk (get-zk options)]
    (doall (map (partial delete-topic! zk) (all-topics zk)))))

(defn -main
  "Manage topics"
  [& args]
  ;; good lord log4j
  (org.apache.log4j.BasicConfigurator/configure)
  (.setLevel  (org.apache.log4j.Logger/getRootLogger) org.apache.log4j.Level/ERROR)

  (let [{:keys [options arguments errors summary]}
        (parse-opts args cli-options)]
    (cond
      (:help options) (exit 0 (usage summary)))
    (case (first arguments)
      "check" (check-topics options)
      "list" (list-topics options)
      "delete" (delete-topics options)
      (exit 1 (usage summary)))))

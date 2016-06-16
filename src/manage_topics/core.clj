;; manage-topics
;;
;; it's a command line tool that manages the state of a cluster's topics
;; from an .ini file.
;;
;; TODO:
;;  + document ini format

(ns manage-topics.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure-ini.core :refer [read-ini]]
            [clojure.set :refer [difference]]
            [clojure.data :refer [diff]]
            [franzy.admin.topics :refer [all-topics
                                         delete-topic!
                                         create-topic!
                                         topics-metadata]]
            [franzy.admin.configuration :refer [all-topic-configs]]
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
   ["-t" "--topics-ini PATH" "path to topics.ini file"
    :default "topics.ini"]
   ["-p" "--partitions N" "override partitions in .ini file"]
   ["-r" "--replication N" "override replication in .ini file"]
   ["-v" "--verbose" "be extra verbose"]
   ["-h" "--help"]])

(defn error [& rest]
  (.println *err* (clojure.string/join " " rest)))

(defn- exit [status msg]
  "Exit the process with a status and message"
  (error msg)
  (System/exit status))

;; special .ini file section whose contents should be applied to all
;; other sections
(def default-key "DEFAULT")

;; map of command line arguments and the .ini options they map to
(def overrides {"partitions" "partitions"
                "replication" "replication_factor"})

;; map of .ini file names and the options as kafka expects to see them
(def kafka-configs {:cleanup_policy "cleanup.policy"
                    :delete_retention_ms "delete.retention.ms"
                    :flush_messages "flush.messages"
                    :flush_ms "flush.ms"
                    :index_interval_bytes "index.interval.bytes"
                    :max_message_bytes "max.message.bytes"
                    :min_cleanable_dirty_ratio "min.cleanable.dirty.ratio"
                    :min_insync_replicas "min.insync.replicas"
                    :retention_bytes "retention.bytes"
                    :retention_ms "retention.ms"
                    :segment_bytes "segment.bytes"
                    :segment_index_bytes "segment.index.bytes"
                    :segment_jitter_ms "segment.jitter.ms"
                    :segment_ms "segment.ms"
                    })

(defn- get-zk-from-options [options]
  "get a zookeeper client"
  (client/make-zk-utils
   {:servers (:zookeeper options)}
   false))

(defn- get-overrides-from-options [options]
  "get configuration overrides from command line arguments"
  (into {} (for [[options-key ini-key] overrides
                 :when (not (nil? (options options-key)))]
             [ini-key (options options-key)])))

(defn- get-target-config-from-options [options]
  "get config from an .ini file at a path defined in options"
  (let [raw-config (read-ini (:topics-ini options)
                             :comment-char \#
                             :keywordize? true)
        defaults (raw-config default-key)
        topics (dissoc raw-config default-key)
        overrides (get-overrides-from-options options)]
    (into {} (for [[k topic-config] topics]
               [k (merge defaults topic-config overrides)]))))

(defn- kafka-config-from-target-config
  "prepare a config blob with string keys to pass to kafka"
  [config]
  (into {} (for [[inikey kafkakey] kafka-configs
                 :when (config inikey)]
             [kafkakey (config inikey)])))

(defn- keywordize-keys
  "run through a hashmap and keywordize its keys"
  [string-keys]
  (into {} (for [[key val] string-keys] [(keyword key) val])))

(defn- check-extra-and-missing
  [ini-topics existing-topics]
  (doseq [missing-topic (difference (set ini-topics)
                                    (set existing-topics))]
    (error "missing topic" missing-topic))
  (doseq [excess-topic (difference (set existing-topics)
                                   (set ini-topics))]
    (error "unknown topic" excess-topic)))


(defn- check-topic-config
  [desired-topic-config actual-config]
  (doseq [desired desired-topic-config]
    (let [topic (first desired)
          desired-translated (keywordize-keys
                              (kafka-config-from-target-config
                               (desired-topic-config topic)))
          found (actual-config topic)]
      (when (not (nil? found))  ;; don't worry over missing topics
        (let [[missing-from-cluster extra-on-cluster _]
              (diff desired-translated found)]
          (when missing-from-cluster
            (error topic "config missing from cluster:" missing-from-cluster)
          (when extra-on-cluster
            (error topic "extra config on cluster:" extra-on-cluster))))))))

(defn- do-check-topics
  "check topics action"
  [options]
  (with-open [zk (get-zk-from-options options)]
    (let [config (get-target-config-from-options options)
          ini-topics (map name (keys config))
          existing-topics (all-topics zk)
          metadata (topics-metadata zk existing-topics) ;; not actually what i need
          actual-config (all-topic-configs zk)]
      ;; TODO check partitions and replication
      ;; TODO filter out __consumer_offsets
      (check-extra-and-missing ini-topics existing-topics)
      (check-topic-config config actual-config))))

(defn- do-create-topics
  "create topics action"
  [options]
  (with-open [zk (get-zk-from-options options)]
    (let [config (get-target-config-from-options options)
          verbose (:verbose options)
          existing-topics (all-topics zk)]
      (doseq [new-topic (difference (set (keys config))
                                    (set (map keyword existing-topics)))]
        ;; log if no diff
        (let [topic-config (config new-topic)
              partitions (Integer. (topic-config :partitions))
              replication (Integer. (topic-config :replication_factor))
              kafka-config (kafka-config-from-target-config topic-config)]
          ;; TODO only print when verbose
          (when verbose
            (println "creating" new-topic)
            (println "replication" replication)
            (println "partitions" partitions)
            (println "with config" kafka-config))
          (create-topic! zk
                         (name new-topic)
                         partitions
                         replication
                         kafka-config))))))

(defn- do-delete-topics
  "delete topics action"
  [options]
  (println "deleting all topics! are you sure? type 'yes'")
  (when (not= (read-line) "yes")
    (exit 1 "bailing out"))
  (with-open [zk (get-zk-from-options options)]
    (doseq [topic (all-topics zk)]
      (delete-topic! zk topic))))

(defn- do-list-topics
  "list topics action"
  [options]
  (with-open [zk (get-zk-from-options options)]
    (doall (map println (sort (all-topics zk))))))

(defn -main
  "Manage topics"
  [& args]
  ;; good lord log4j
  (org.apache.log4j.BasicConfigurator/configure)
  (.setLevel (org.apache.log4j.Logger/getRootLogger)
             org.apache.log4j.Level/ERROR)

  (let [{:keys [options arguments errors summary]}
        (parse-opts args cli-options)]
    (when (:help options)
      (exit 0 (usage summary)))
    (case (first arguments)
      "check" (do-check-topics options)
      "create" (do-create-topics options)
      "delete" (do-delete-topics options)
      "list" (do-list-topics options)
      (exit 1 (usage summary)))))

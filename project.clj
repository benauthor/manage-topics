(defproject manage-topics "0.1.0"
  :description "Create a kafka producer and high level consumer"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.3.3"]
                 [ymilky/franzy-admin "0.0.1"]]
                 ;; [org.apache.kafka/kafka_2.9.2 "0.8.1.1"
                 ;;  :exclusions [javax.jms/jms
                 ;;               com.sun.jdmk/jmxtools
                 ;;               com.sun.jmx/jmxri]]]
  :aot [manage-topics.core]
  :main manage-topics.core)

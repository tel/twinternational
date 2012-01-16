(defproject sdbo/twinternational "0.0.1-SNAPSHOT"
  :description "Collecting data on Twinternational Trends."
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [pipes/pipes "0.0.2"]
                 [cheshire "2.0.6"]
                 [http.async.client "0.4.0"]
                 [slingshot "0.10.1"]
                 [congomongo "0.1.7"]]
  :repl-options [:init nil :caught clj-stacktrace.repl/pst+])
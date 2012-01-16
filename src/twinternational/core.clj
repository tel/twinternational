(ns twinternational.core
  (:require [pipes.core :as p]
            [somnium.congomongo :as cm])
  (:use [cheshire.core]
        [clojure.string :only [split-lines]]
        [slingshot.slingshot :only [throw+ try+]])
  (:import [java.text SimpleDateFormat]
           [java.util Date]))

(defn parse-date [str]
  (-> (doto (SimpleDateFormat. "EEE MMM dd HH:mm:ss ZZZZZ yyyy"
                               java.util.Locale/ENGLISH)
        (.setLenient false))
      (.parse str)
      .getTime))

(defn count-conduit [atom]
  (p/conduit []
    (pass [xs] (swap! atom inc) xs)))

(let [mornings (split-lines (slurp "resources/morning.lst"))
      nights   (split-lines (slurp "resources/night.lst"))
      morning-pattern (re-pattern
                       (apply str (interpose "|" mornings)))
      night-pattern   (re-pattern
                       (apply str (interpose "|" nights)))
      found? (comp not not)
      tag-tweet (fn tag-tweet [tweet tag pattern]
                  (assoc tweet tag (found? (re-find pattern (:text tweet)))))]
  (defn type-map
    "Set the apropriate MORNING/NIGHT type for a particular tweet."
    [tweet]
    (-> tweet
        (tag-tweet :morning morning-pattern)
        (tag-tweet :night night-pattern)))
  (defn twt-source []
    (p/left-fuse
     (p/streaming-http-source
      :post "https://stream.twitter.com/1/statuses/filter.json"
      :query {"track" (apply str (interpose "," (concat mornings nights)))}
      :auth {:user "btwintern" :password "zpassword"})
     (p/lines-conduit)
     (p/nothing-on-error (p/map-conduit #(parse-string % true)))
     (p/filter-conduit :text)
     (p/map-conduit type-map)
     (p/filter-conduit (every-pred :geo (some-fn :morning :night)))
     (p/map-conduit (fn [tweet]
                      (let [{:keys [geo created_at morning]} tweet]
                        {:t (parse-date created_at)
                         :line (if morning "morning" "night")
                         :lat (first (:coordinates geo))
                         :lon (second (:coordinates geo))}))))))

(defn mongo-write-sink [collection db conn & {:keys [user pass]}]
  (p/sink [conn (if user
                  (doto (cm/make-connection db conn)
                    (cm/authenticate user pass))
                  (cm/make-connection db conn))]
    (update [vals]
      (cm/with-mongo conn
        (doseq [val vals]
          (cm/insert! collection val)))
      (p/yield))
    (close []
      (cm/close-connection conn)
      (p/yield nil))))

(defn -main [& args]
  (let [mongolab_uri (System/getenv "MONGOLAB_URI")
        
        [all username password host port database]
        (re-find #"^mongodb://([^:]+):([^@]+)@([^:]+):([1-9]+)/(.*)$"
                 mongolab_uri)]
    (p/connect
     (p/source-repeatedly
      (p/eof-on-error
       (twt-source)))
     (mongo-write-sink :tweets
                       database {:host host :port (Integer/parseInt port)}
                       :user username
                       :pass password))))
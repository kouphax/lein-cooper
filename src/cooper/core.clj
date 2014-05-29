(ns cooper.core
  (:require [me.raynes.conch.low-level :as sh]
            [primrose.core :as primrose]
            [clojure.java.io :refer [reader]]
            [clansi :refer [style]]
            [clojure.string :refer [split trim]])
  (:gen-class))

(def pallette (cycle [:green :yellow :blue :magenta :red]))

(defn pretty-pipe [process-name process colour]
  (future
    (let [output (reader (:out process)) ]
      (loop [out (.readLine output)]
        (when-not (nil? out)
          (println (str (style process-name colour) ": " out)))
        (recur (.readLine output))))))

(defn get-procs []
  (with-open [buffer (reader "Procfile")]
    (doall
      (for [line (line-seq buffer)]
        (let [[name command] (split line #":")
              command-seq (-> command trim (split #" "))]
          (assoc (apply sh/proc command-seq) :name name))))))

(defn pipe-procs [procs]
  (doseq [proc procs
          index (range 0 (count procs))]
    (pretty-pipe (:name proc) proc (nth pallette index))))

(defn -main [& args]
  (let [procs (get-procs)]
    (pipe-procs procs)
    (deref
      (apply
        primrose/first
        (map #(future (sh/exit-code %)) procs)))
    (doseq [proc procs]
      (sh/destroy proc))
    (println "DONE______________!f")
    (System/exit 0)))


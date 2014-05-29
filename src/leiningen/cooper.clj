(ns leiningen.cooper
  (:require [me.raynes.conch.low-level :as sh]
            [primrose.core :as primrose]
            [clojure.java.io :refer [reader]]
            [clansi :refer [style]]
            [clojure.string :refer [split trim join]]))

(def pallette (cycle [:green :blue :yellow :magenta :red]))

(defn pretty-pipe [process-name process colour]
  (doseq [stream [(:out process) (:err process)]]
    (future
      (let [output (reader stream) ]
        (loop [out (.readLine output)]
          (when-not (nil? out)
            (println (str (style process-name colour) ": " out)))
          (recur (.readLine output)))))))

(defn get-procs []
  (with-open [buffer (reader "Procfile")]
    (doall
      (for [line (line-seq buffer)]
        (let [matcher (re-matcher #"([^:]*):(.*)" line)
              [_ name command] (re-find matcher)
              command-seq (-> command trim (split #" "))]
          (assoc (apply sh/proc command-seq) :name name))))))

(defn pipe-procs [procs]
  (let [names (map :name procs)
        colours (take (count procs) pallette)]
    (doall
      (map pretty-pipe names procs colours))))

(defn cooper
  "I don't do a lot."
  [project & args]
  (let [procs (get-procs)]
    (pipe-procs procs)
    (deref
      (apply
        primrose/first
        (map #(future (sh/exit-code %)) procs)))
    (doseq [proc procs]
      (println "Killing " (:name proc))
      (sh/destroy proc))
    (leiningen.core.main/abort)))

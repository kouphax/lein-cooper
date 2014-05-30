(ns leiningen.cooper
  (:require [me.raynes.conch.low-level :as sh]
            [primrose.core :as primrose]
            [clojure.java.io :as io]
            [clansi :refer [style]]
            [clojure.string :refer [split trim join]]
            [leiningen.core.main :refer [abort]]))

(def pallette (cycle [:green :blue :yellow :magenta :red]))

(defn pretty-pipe [process-name process colour]
  (doseq [stream [(:out process) (:err process)]]
    (future
      (let [output (io/reader stream) ]
        (loop [out (.readLine output)]
          (when-not (nil? out)
            (println (str (style process-name colour) ": " out)))
          (recur (.readLine output)))))))

(defn get-procs []
  (with-open [buffer (io/reader "Procfile")]
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

(defn ensure-procfile-exists []
  (when-not (.exists (io/file "Procfile"))
    (println (style "Procfile does not exist" :red))
    (abort)))

(defn fail [procs]
  (println (style "
One or more processes have stopped running. In the current
version of lein-cooper this could result in child processes
(i.e. ones spawned by a process that lein-cooper manages)
left hanging that may need killed manually

Sorry about the inconvenience." :red))
    (println)
    (doseq [proc procs]
      (sh/destroy proc))
    (abort))

(defn wait-for-early-exit [procs]
  (deref
    (apply primrose/first
      (map #(future (sh/exit-code %)) procs))))

(defn cooper [project & args]
  (ensure-procfile-exists)
  (doto (get-procs)
        (pipe-procs)
        (wait-for-early-exit)
        (fail)))

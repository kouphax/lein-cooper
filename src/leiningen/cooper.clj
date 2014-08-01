(ns leiningen.cooper
  (:require [me.raynes.conch.low-level :as sh]
            [primrose.core :as primrose]
            [clojure.java.io :as io]
            [clansi :refer [style]]
            [clojure.string :refer [split trim join]]
            [leiningen.core.main :refer [abort]]))

(def ^:private pallette (cycle [:green :blue :yellow :magenta :red]))

(defn- pretty-pipe [process-name process colour]
  (doseq [stream [(:out process) (:err process)]]
    (future
      (let [output (io/reader stream) ]
        (loop [out (.readLine output)]
          (when-not (nil? out)
            (println (str (style process-name colour) ": " out)))
          (recur (.readLine output)))))))

(defn- get-procs []
  (with-open [buffer (io/reader "Procfile")]
    (doall
      (for [line (line-seq buffer)]
        (let [matcher (re-matcher #"([^:]*):(.*)" line)
              [_ name command] (re-find matcher)
              command-seq (-> command trim (split #" "))]
          (assoc (apply sh/proc command-seq) :name name))))))

(defn- pipe-procs [procs]
  (let [names (map :name procs)
        colours (take (count procs) pallette)]
    (doall
      (map pretty-pipe names procs colours))))

(defn- ensure-procfile-exists [procfile]
  (when-not (.exists (io/file procfile))
    (println (style (str procfile " does not exist") :red))
    (abort)))

(defn- fail [procs]
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

(defn- wait-for-early-exit
  "Blocks until at least one of the running processes produces an exit code
   It is irrelevant what that code is.  If any of the stop running thats it."
  [procs]
  (deref
    (apply primrose/first
      (map #(future (sh/exit-code %)) procs))))

(defn cooper
  "The cooper plugin is used to combine multiple long runnning processes and
   pipe their output and error streams to `stdout` in a distinctive manner.

   Cooper follows the standard set out by Rubys Foreman gem and processes are
   defined in a Procfile at the root of the project.  Each line is a process
   name and related command.  A sample Procfile looks like this,

       web: lein ring server
       jsx: jsx --watch src/ build/

   This example defines 2 processes web and jsx.

   - web runs the ring server
   - jsx runs the react.js precompiler in auto compile mode.

   This avoids having to manage checking on multiple windows and checking their
   output.

   **CAUTION**

   The JVM is super pants at managing external processes which means that when
   a processes dies and cooper attempts to kill the other processes there may
   be some processes left running.  This is due to the fact that when the JVM
   kills processes it wont kill child process of that process.  There is also
   no cross paltform way to get a handle on child processes and kill them.

   However this is only an issue when a process fails.  When you manually
   CTRL-C out of the lein cooper command everything will be shutdown as
   expected so this issue only happens in an error case."
  [project & args]
  (let [procfile (or (first args) "Procfile")]
    (ensure-procfile-exists procfile))
  (doto (get-procs)
        (pipe-procs)
        (wait-for-early-exit)
        (fail)))

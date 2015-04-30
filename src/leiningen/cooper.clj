(ns leiningen.cooper
  (:require [me.raynes.conch.low-level :as sh]
            [primrose.core :as primrose]
            [clojure.java.io :as io]
            [clansi :refer [style]]
            [clojure.string :refer [split trim join]]
            [leiningen.core.main :refer [abort]]))

(def ^:private pallette (cycle [:green :blue :yellow :magenta :red]))

(def ^:private time-formatter (java.text.SimpleDateFormat. "HH:mm:ss"))

(defn- current-time []
  (.format time-formatter (java.util.Date.)))

(defn- pretty-pipe [process-name process colour]
  (doseq [stream [(:out process) (:err process)]]
    (future
      (let [output (io/reader stream) ]
        (loop [out (.readLine output)]
          (when-not (nil? out)
            (println (str (style (str (current-time) " " process-name) colour) "| " out)))
          (recur (.readLine output)))))))

(defn- calculate-padding
  "Takes a list of process metadata and calculates the padded name. This is
   to ensure our logging output is aligned by finding the longest process out
   of the list and padding the other names enough so that

     07:46:33 web: ...
     07:46:34 db: ...
     07:46:34 scheduler: ...

   becomes,

     07:46:33 web       : ...
     07:46:34 db        : ...
     07:46:34 scheduler : ...

   Which is easier to skim when reading output."
  [procs]
  (let [characters-in-names (map #(count (:name %)) procs)
        longest             (apply max characters-in-names)]
    (doall
      (for [proc procs]
        (let [format-string (str "%-" (inc longest) "s")
              padded-name   (format format-string (:name proc)) ]
          (assoc proc :padded-name padded-name))))))

(defn- get-procs []
  (with-open [buffer (io/reader "Procfile")]
    (doall
      (for [line (line-seq buffer)]
        (let [matcher (re-matcher #"([^:]*):(.*)" line)
              [_ name command] (re-find matcher)
              command-seq (-> command trim (split #" "))]
          (assoc (apply sh/proc command-seq) :name name))))))

(defn- pipe-procs [procs]
  (let [names (map :padded-name procs)
        colours (take (count procs) pallette)]
    (doall
      (map pretty-pipe names procs colours))))

(defn- ensure-procfile-exists []
  (when-not (.exists (io/file "Procfile"))
    (println (style "Procfile does not exist" :red))
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

(defn  ^:no-project-needed cooper
  "Combine multiple long runnning processes and pipe their output and error
   streams to `stdout` in a distinctive manner.

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
  (ensure-procfile-exists)
  (doto (-> (get-procs) calculate-padding)
        (pipe-procs)
        (wait-for-early-exit)
        (fail)))


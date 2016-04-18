[![Clojars Project](http://clojars.org/lein-cooper/latest-version.svg)](http://clojars.org/lein-cooper)

# lein-cooper

> __cooper__ / co-op (per) / co-operative processes.

A Leiningen plugin that can be used to combine multiple long runnning processes and pipe their output and error streams to `stdout` in a distinctive manner.

A __long running process__ in this case is one that runs indefinitley and requires human interaction to stop.  Cooper will not play well with something that runs for a while and stops when it is done.  Think server processes or file watchers and auto test runners.

`lein-cooper` follows the standard set out by Rubys [Foreman](https://github.com/ddollar/foreman) gem and processes are defined in a `Procfile` at the root of the project.  Each line is a process name and related command.  A sample Procfile looks like this,

```
web: lein ring server
jsx: jsx --watch src/ build/
```

This example defines 2 processes web and jsx.

- `web` runs the ring server
- `jsx` runs the react.js precompiler in auto compile mode.

This avoids having to manage checking on multiple windows and checking their output.

When processes spit out information to their `out` or `err` streams they are labelled and colour coded for easy distinction.

> __** CAUTION **__
>
 > The JVM is super pants at managing external processes which means that when a processes dies and cooper attempts to kill the other processes there may be some processes left running.  This is due to the fact that when the JVM kills processes it wont kill child process of that process.  There is also no cross paltform way to get a handle on child processes and kill them.
>
> However this is only an issue when a process fails.  When you manually CTRL-C out of the lein cooper command everything will be shutdown as expected so this issue only happens in an error case.

## Use Cases

My need for this came about because I was tinkering with a Clojurescript project and needed to do 2 things.

1. Run [lein-simpleton](https://github.com/tailrecursion/lein-simpleton) to serve my static content
2. Run [lein-cljsbuild](https://github.com/emezeske/lein-cljsbuild) in `auto` mode to compile my clojurescript on the fly.

But any time you have more than one long running process (it needn't be a leiningen task of course, so any sort of web precompilers or auto test runners) then this will bring all the output into a nice single stream.

> This was created for a personal need and probably goes against typical Clojurarian workflows but everyone has their own flow.  Maybe this will help someone.

## Usage

### user-level plugins:

Put `[lein-cooper "1.2.2"]` into the `:plugins` vector of your
`:user` profile.

### project-level plugins:

Put `[lein-cooper "1.2.2"]` into the `:plugins` vector of your project.clj.

### Manage your processes in `Procfile` or `project.clj`:

#### Work with Procfile

A procfile is a file containing lists of names processes,

```
<name>:<command to run>
<name>:<command to run>
<name>:<command to run>
```

For example

```
cljs:  lein cljsbuild auto
web: lein simpleton 8000
```

The `Procfile` should live at the root of your project.

```shell
 $ lein cooper
 ```

#### Work with project.clj

Alternatively, you can define your processes in `project.clj` with `:cooper`.

```clojure
:cooper {"cljs" ["lein" "cljsbuild" "auto"]
         "web"  ["lein" "simpleton" "8000"]}
```

### Running

To run all processes, use `lein cooper`

To run named processes, use something like `lein cooper cljs web`.

## License

Copyright © 2015 James Hughes

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

## Contributors

```
    20  James Hughes
     4  Mike Schaeffer
     2  DogLooksGood
     1  Tianshu Shi
     1  Jon Neale
     1  Antonin Hildebrand
```

(ns raven.core
  (:require [lumo.core]
            [fs]
            [dgram]
            [process       :as ps]
            [child_process :as cp]
            [osc-msg       :as osc]))

(def server          {:host "127.0.0.1" :port "57110"})
(def udp-socket      (atom (dgram/createSocket "udp4")))
(def current-node    (atom 2))
(def current-buffer  (atom 0))
(def current-session (atom []))

(defn io-error [err]
  (when err (println "IO Error: " err)))

(defn timestamp []
  (.toISOString (js/Date.)))

(def current-session-path
  (str (ps/cwd) "/events/session" (timestamp) ".edn"))

(defn add-session-watch []
  (add-watch current-session
             :fs-sync (fn [_ _ _ state]
                        (fs/writeFile current-session-path
                                      state
                                      io-error))))

(defn store-event
  "Append event (with timestamp) to current-session atom"
  [event]
  (->> (conj event {:timestamp (timestamp)})
       (swap! current-session conj)))

(defn send
  "Encode OSC message & send to SCSynth"
  [msg]
  (let [enc-msg (osc/encode (clj->js msg))
        enc-len (.-length enc-msg)
        {:keys [host port]} server]
    (.send @udp-socket enc-msg 0 enc-len port host)))

(defn nest
  "Create group on node 1"
  []
  (let [msg {:address "/g_new"
             :args [{:type "integer" :value 1}
                    {:type "integer" :value 1}
                    {:type "integer" :value 0}]}]
    (store-event {:type "nest" :msg msg})
    (send msg))
  "Building a nest on a distant power line...")

(defn find-type
  "Wrap value with type-specific hash-map"
  [val]
  (cond
    (integer? val) {:type "integer" :value val}
    (float? val)   {:type "float"   :value val}
    (string? val)  {:type "string"  :value val}
    :else          {:type "integer" :value 0}))

(defn lay
  [synthdef & args]
  "Create a new synth"
  (let [node (swap! current-node inc)
        msg  {:address "/s_new"
              :args (into [{:type "string"  :value synthdef}
                           {:type "integer" :value @node}
                           {:type "integer" :value 1}
                           {:type "integer" :value 1}]
                          (mapv find-type args))}]
    (store-event {:type "lay" :msg msg})
    (send msg)
    (println "An egg is hatching...")
    @node))

(defn croak
  "Set a node's control value"
  [node k v]
  (let [msg {:address "/n_set"
             :args [{:type "integer" :value node}
                    {:type "string"  :value k}
                    {:type "integer" :value v}]}]
    (store-event {:type "croak" :msg msg})
    (send msg)
    "Croaking..."))

(defn fly
  "Free node 1"
  []
  (let [msg {:address "/n_free"
             :args [{:type "integer" :value 1}]}]
    (store-event {:type "fly" :msg msg})
    (send msg)
    (swap! current-node #(-> 2))
    "Abandoning nest..."))

(defn load-buf
  "Allocate buffer & read sound file"
  [path]
  (swap! current-buffer inc)
  (send {:address "/b_allocRead"
             :args [{:type "integer" :value @current-buffer}
                    {:type "string"  :value path}
                    {:type "integer" :value 0}
                    {:type "integer" :value 0}]}))

(defn samples
  "Load a directory of sound files"
  [dir]
  (fs/readdir dir (fn [err files]
                    (if err
                      (io-error err)
                      (doall (map load-buf files))))))

(defn load-defs
  "Load a directory of synth definitions"
  []
  (let [msg {:address "/d_loadDir"
             :args [{:type "string" :value (str (ps/cwd) "/synths/")}]}]
    (send msg)))

(defn compile
  "Format & compile synth definitions in synthDefs.scd (via SCLang)"
  []
  (let [writedef-str    (str "writeDefFile(\"" (ps/cwd) "/synths/\")")
        replace-deffile #(clojure.string.replace % #"writeDefFile" writedef-str)
        replace-newline #(clojure.string.replace % #"\r\n|\n|\r" "")
        create-command  #(str "echo '" % "' | sclang")]
    (fs/readFile "synthDefs.scd" "utf8" (fn [err data]
                                          (if err
                                            (io-error err)
                                            (->> data
                                                 replace-newline
                                                 replace-deffile
                                                 create-command
                                                 cp/exec))))))

(defn reload-defs
  "Compile & reload synth definitions"
  []
  (compile)
  (load-defs))

(defn synths-dir-check
  "If synths directory doesn't exist, create it"
  []
  (let [dir-path   (str (ps/cwd) "/synths/")
        access-err #(when % (fs/mkdir dir-path io-error))]
    (fs/access dir-path fs.constants.F_OK access-err)))

(defn events-dir-check
  "If events directory doesn't exist, create it"
  []
  (let [dir-path   (str (ps/cwd) "/events/")
        access-err #(when % (fs/mkdir dir-path io-error))]
    (fs/access dir-path fs.constants.F_OK access-err)))

(defn session-check
  "If session file doesn't exist, create it"
  []
  (try (fs/accessSync current-session-path fs.constants.F_OK)
       (catch :default err (fs/appendFileSync current-session-path "[]"))))

(defn init []
  (synths-dir-check)
  (events-dir-check)
  (add-session-watch)
  (reload-defs))

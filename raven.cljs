(require '[cljs.nodejs :as nodejs])
(def fs (nodejs/require "fs"))
(def process (nodejs/require "process"))
(def child-process (nodejs/require "child_process"))
(def dgram (nodejs/require "dgram"))
(def oscmsg (nodejs/require "osc-msg"))

(def socket (.createSocket dgram "udp4"))
(def current-node (atom 2))
(def current-buffer (atom 0))

(defn timestamp []
  (.toISOString (new js/Date)))

(defn io-error [err]
  (when err
    (println "IO Error")))

(def current-session-path (str (.cwd process) "/events/session" (timestamp) ".json"))
(def current-session (atom []))

(add-watch current-session
           :fs-sync
           (fn [_ _ _ state]
             (.writeFile fs current-session-path state io-error)))

(defn json->clj [data]
  (js->clj (.parse js/JSON data) :keywordize-keys true))

(defn clj->json [data]
  (.stringify js/JSON (clj->js data)))

(defn store-event
  "Append timestamp to event & conj to current-session atom"
  [event]
  (->> (conj event {:timestamp (timestamp)})
       (swap! current-session conj)))

(defn send-msg
  "Encode OSC message & send to SCSynth"
  [msg]
  (let [buf (.encode oscmsg (clj->js msg))]
    (.send socket buf 0 (.-length buf) "57110" "127.0.0.1")))

(defn nest
  "Create group on node 1"
  []
  (store-event {:type "nest"})
  (send-msg {:address "/g_new"
             :args [{:type "integer" :value 1}
                    {:type "integer" :value 1}
                    {:type "integer" :value 0}]})
  "Building a nest on a distant power line...")

(defn find-type
  "Return type-specific hash-map depending on value"
  [val]
  (cond
    (integer? val) {:type "integer" :value val}
    (float? val) {:type "float" :value val}
    (string? val) {:type "string" :value val}
    :else {:type "integer" :value 0}))

(defn lay
  "Create a new synth"
  [synthdef & args]
  (swap! current-node inc)
  (store-event {:type "lay"})
  (send-msg {:address "/s_new"
             :args (into [{:type "string" :value synthdef}
                          {:type "integer" :value @current-node}
                          {:type "integer" :value 1}
                          {:type "integer" :value 1}]
                         (mapv find-type args))})
  (println "An egg is hatching...")
  @current-node)

(defn croak
  "Set a node's control value"
  [node k v]
  (store-event {:type "croak"})
  (send-msg {:address "/n_set"
             :args [{:type "integer" :value node}
                    {:type "string" :value k}
                    {:type "integer" :value v}]})
  "Croaking...")

(defn fly
  "Free node 1"
  []
  (store-event {:type "fly"})
  (send-msg {:address "/n_free"
             :args [{:type "integer" :value 1}]})
  "Abandoning nest...")

(defn load-buf
  "Allocate buffer & read sound file"
  [path]
  (swap! current-buffer inc)
  (send-msg {:address "/b_allocRead"
             :args [{:type "integer" :value @current-buffer}
                    {:type "string" :value path}
                    {:type "integer" :value 0}
                    {:type "integer" :value 0}]}))

(defn samples
  "Load a directory of sound files"
  [dir]
  (.readdir
   fs dir
   (fn [err files]
     (if err
       (println "IO Error: Unable to find directory")
       (doall (map load-buf files))))))

(defn load-defs
  "Load a directory of synth definitions"
  []
  (send-msg {:address "/d_loadDir"
             :args [{:type "string"
                     :value (str (.cwd process) "/synths/")}]}))

(defn compile
  "Format & compile synth definitions in synthDefs.scd (via SCLang)"
  []
  (let [replace-newline #(clojure.string.replace % #"\r\n|\n|\r" "")
        writedef-str (str "writeDefFile(\"" (.cwd process) "/synths/\")")
        replace-deffile #(clojure.string.replace % #"writeDefFile" writedef-str)]
    (.readFile
     fs "synthDefs.scd" "utf8"
     (fn [err data]
       (when (not err)
         (let [synthdefs (-> (replace-newline data)
                             (replace-deffile))]
           (child-process.exec (str "echo '" synthdefs "' | sclang"))))))))

(defn reload-defs
  "Compile & reload synth definitions"
  []
  (compile)
  (load-defs))

(defn dir-check
  "If synths directory doesn't exist, create it"
  []
  (let [dir-path (str (.cwd process) "/synths/")
        mkdir-err #(when % (throw (js/Error. "IO Error: Unable to create directory")))
        access-err #(when % (.mkdir fs dir-path mkdir-err))]
    (.access fs dir-path fs.constants.F_OK access-err)))

(defn session-check
  "If session file doesn't exist, create it"
  []
  (try (.accessSync fs current-session-path fs.constants.F_OK)
       (catch :default err (.appendFileSync fs current-session-path "[]"))))

(dir-check)
(reload-defs)
(session-check)

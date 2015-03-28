(defproject openlayers-om-components "0.1.0-SNAPSHOT"
  :description "Om Components which leverage Openlayers to provide interative maps for use in data capture apps"
  :url "https://github.com/condense/openlayers-om-components"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0-alpha5"]
                 [org.clojure/clojurescript "0.0-3126"]
                 [figwheel "0.2.5"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [sablono "0.3.4"]
                 [org.omcljs/om "0.8.8"]
                 [cljsjs/openlayers "3.3.0-0"]]

  :plugins [[lein-cljsbuild "1.0.5"]
            [lein-figwheel "0.2.5"]]

  :source-paths ["src"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled"]
  
  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src" "dev_src"]
              :compiler {:output-to "resources/public/js/compiled/openlayers_om_components.js"
                         :output-dir "resources/public/js/compiled/out"
                         :optimizations :none
                         :main openlayers-om-components.dev
                         :libs ["lib/simplegeometry.js" "lib/transformflatgeom.js" "lib/scaleinteraction.js" "lib/translateinteraction.js"]
                         :asset-path "js/compiled/out"
                         :source-map true
                         :source-map-timestamp true
                         :cache-analysis true }}
             {:id "min"
              :source-paths ["src"]
              :compiler {:output-to "resources/public/js/compiled/openlayers_om_components.js"
                         :main openlayers-om-components.core
                         :libs ["lib/simplegeometry.js" "lib/transformflatgeom.js" "lib/scaleinteraction.js" "lib/translateinteraction.js"]
                         :externs ["ext/olx.js"]
                         :optimizations :advanced
                         :pretty-print false
                         :elide-asserts true
                         :closure-warnings {:externs-validation :off
                                            :non-standard-jsdoc :off}}}]}

  :figwheel {
             :http-server-root "public" ;; default and assumes "resources" 
             :server-port 3449 ;; default
             :css-dirs ["resources/public/css"] ;; watch and update CSS

             ;; Start an nREPL server into the running figwheel process
             ;; :nrepl-port 7888

             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is simple ring servers, if this
             ;; doesn't work for you just run your own server :)
             ;; :ring-handler hello_world.server/handler

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;; :server-logfile "tmp/logs/figwheel-logfile.log" 
             })

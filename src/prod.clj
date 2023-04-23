(ns prod
  (:gen-class)
  (:require app.media-player ; in prod, load app into server so it can accept clients
            clojure.string
            electric-server-java8-jetty9))

(when (clojure.string/blank? (System/getProperty "HYPERFIDDLE_ELECTRIC_SERVER_VERSION"))
  (throw (ex-info "$HYPERFIDDLE_ELECTRIC_SERVER_VERSION must be set in prod" {})))

(def electric-server-config
  {:host "0.0.0.0", :port 8080, :resources-path "public"})

(defn -main [& args] ; run with `clj -M -m prod`
  (electric-server-java8-jetty9/start-server! electric-server-config))

; On CLJS side we reuse src/user.cljs for prod entrypoint
(ns nginx.clojure.fns-for-test
  (:import [nginx.clojure Coroutine]
           [java.util ArrayList]))

(defn echo [^ArrayList ma msg]
  (.add ma (str "before yield:" msg))
  (Coroutine/yield)
  (.add ma (str "end yield:" msg))
  )

(defn simplefn [^ArrayList ma]
  (.add ma "entering simplefn")
  (Coroutine/yield)
  (doseq [i (range 3)]
    (apply echo [ma i])
    )
  (.add ma (str "threadId:" (.getId (Thread/currentThread)))))

(defn echo-out-of-coroutine [^ArrayList ma msg]
  (.add ma (str "echo:" msg)))

(defn fn-out-of-coroutine [^ArrayList ma]
  (.add ma "entering fn-out-of-coroutine")
  (doseq [i (range 3)]
    (apply echo-out-of-coroutine [ma i])
    ))
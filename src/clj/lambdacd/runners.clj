(ns lambdacd.runners
  (:require [lambdacd.internal.execution :as execution]
            [clojure.core.async :as async]
            [lambdacd.event-bus :as event-bus]))

(defn- is-first-step? [step-finished-msg]
  (= (:step-id step-finished-msg) [1]))

(defn- is-not-retriggered? [step-finished-msg]
  (not (:retrigger-mock-for-build-number (:final-result step-finished-msg))))

(def should-trigger-next-build?
  (every-pred is-first-step? is-not-retriggered?))

(defn start-new-run-after-first-step-finished
  "Runner that makes sure there is always one pipeline-run with the first step active. useful if the first step is waiting for
  a trigger such as a VCS commit and you don't want to wait for the whole pipeline to complete before starting the next run.
  Do not use this if your first step finishes immediately as this will lead to lots of active pipelines"
  [{pipeline-def :pipeline-def context :context}]
  (let [subscription (event-bus/subscribe context :step-finished)
        steps-finished (event-bus/only-payload subscription)
        first-steps-finisehd (async/filter< should-trigger-next-build? steps-finished)
        pipeline-run-fn (fn [] (async/thread (execution/run pipeline-def context)))]
   (pipeline-run-fn)
   (async/thread
     (while true
       (async/<!! first-steps-finisehd)
       (pipeline-run-fn)))))

(defn start-one-run-after-another
  "Runner that always keeps one pipeline-run active. It waits for a run to complete, then starts a new one."
  [{pipeline-def :pipeline-def context :context}]
 (async/thread
   (while true
     (execution/run pipeline-def context))))
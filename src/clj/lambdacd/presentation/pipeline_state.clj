(ns lambdacd.presentation.pipeline-state
  (:require [lambdacd.util :as util]
            [lambdacd.internal.pipeline-state :as pipeline-state]))

(defn- desc [a b]
  (compare b a))

(defn- asc [a b]
  (compare a b))

(defn- root-step? [[step-id _]]
  (= 1 (count step-id)))

(defn- root-step-id [[step-id _]]
  (first step-id))

(defn- step-result [[_ step-result]]
  step-result)

(defn- status-for-steps [step-ids-and-results]
  (let [accumulated-status (->> step-ids-and-results
                                (filter root-step?)
                                (sort-by root-step-id desc)
                                (first)
                                (step-result)
                                (:status))]
    (or accumulated-status :unknown)))

(defn- not-waiting? [result]
  (not (:has-been-waiting result)))

(defn not-retriggered? [result]
  (not (:retrigger-mock-for-build-number result)))

(defn- first-with-key-ordered-by [comp key steps]
  (->> steps
       (filter not-waiting?)
       (map key)
       (sort comp)
       (first)))

(defn- earliest-first-update [steps]
  (->> steps
      (filter not-retriggered?)
      (first-with-key-ordered-by asc :first-updated-at)))

(defn- latest-most-recent-update [steps]
  (first-with-key-ordered-by desc :most-recent-update-at steps))

(defn- history-entry [[build-number step-ids-and-results]]
  (let [step-results (vals step-ids-and-results)]
    {:build-number build-number
     :status (status-for-steps step-ids-and-results)
     :most-recent-update-at (latest-most-recent-update step-results)
     :first-updated-at (earliest-first-update step-results)}))

(defn history-for [state]
  (sort-by :build-number (map history-entry state)))

(defn most-recent-build-number-in [state]
  (apply max (keys state)))


(defn most-recent-step-result-with [key ctx]
  (let [state (pipeline-state/get-all (:pipeline-state-component ctx))
        step-id (:step-id ctx)
        step-results (map second (reverse (sort-by first (seq state))))
        step-results-for-id (map #(get % step-id) step-results)
        step-results-with-key (filter key step-results-for-id)]
    (first step-results-with-key)))

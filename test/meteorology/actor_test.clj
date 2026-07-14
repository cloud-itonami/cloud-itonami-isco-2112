(ns meteorology.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [meteorology.actor :as actor]
            [meteorology.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-station! st {:station-id "stn-1" :name "Weather Observatory A" :location "42.3601°N, 71.0589°W"})
    (store/register-dataset! st {:dataset-id "ds-1" :station-id "stn-1" :description "2024-07 observations"})
    (store/register-model! st {:model-id "mdl-1" :name "WRF" :version "4.3"})
    st))

(deftest commits-a-clean-low-risk-analysis-request
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:station-id "stn-1" :dataset-id "ds-1" :op :analyze-weather-data :stake :low}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "stn-1"))))))

(deftest holds-on-unregistered-station-without-committing
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:station-id "no-such-station" :op :analyze-weather-data :stake :low :dataset-id "ds-1"}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "no-such-station")))
    (is (= :hold (:disposition (:state result))))))

(deftest holds-on-missing-dataset-for-analyze-op
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:station-id "stn-1" :dataset-id "no-such-ds" :op :analyze-weather-data :stake :low}
        result (actor/run-request! graph request {} "thread-3")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "stn-1")))
    (is (= :hold (:disposition (:state result))))))

(deftest holds-on-missing-model-for-request-model-run
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:station-id "stn-1" :model-id "no-such-model" :op :request-model-run :stake :medium}
        result (actor/run-request! graph request {} "thread-4")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "stn-1")))
    (is (= :hold (:disposition (:state result))))))

(deftest interrupts-then-commits-on-human-approval-for-severe-weather-flag
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; flag-severe-weather-risk always escalates (governor invariant)
        request {:station-id "stn-1" :op :flag-severe-weather-risk :stake :high}
        interrupted (actor/run-request! graph request {} "thread-5")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "stn-1")))
    (let [resumed (actor/approve! graph "thread-5")]
      (is (= :done (:status resumed)))
      (is (some? (get-in resumed [:state :record])))
      (is (= 1 (count (store/records-of st "stn-1")))))))

(deftest holds-on-published-forecast-claim-in-draft
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; forecast proposals claiming publication are hard-rejected
        request {:station-id "stn-1" :op :draft-forecast :stake :high}
        result (actor/run-request! graph request {} "thread-6")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "stn-1")))
    (is (= :hold (:disposition (:state result))))))

(deftest holds-on-auto-issued-warning
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; auto-issue of warnings is hard-rejected (safety critical)
        request {:station-id "stn-1" :op :flag-severe-weather-risk :stake :high}
        result (actor/run-request! graph request {} "thread-7")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "stn-1")))
    (is (= :hold (:disposition (:state result))))))

(deftest interrupts-then-commits-on-human-approval-for-hazardous-forecast
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; draft-forecast with hazard? true escalates (governor invariant)
        request {:station-id "stn-1" :op :draft-forecast :stake :high :hazard? true}
        interrupted (actor/run-request! graph request {} "thread-8")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "stn-1")))
    (let [resumed (actor/approve! graph "thread-8")]
      (is (= :done (:status resumed)))
      (is (some? (get-in resumed [:state :record])))
      (is (= 1 (count (store/records-of st "stn-1")))))))

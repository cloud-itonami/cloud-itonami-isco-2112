(ns meteorology.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [meteorology.governor :as governor]
            [meteorology.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-station! st {:station-id "stn-1" :name "Weather Observatory A" :location "42.3601°N, 71.0589°W"})
    (store/register-dataset! st {:dataset-id "ds-1" :station-id "stn-1" :description "2024-07 hourly observations"})
    (store/register-model! st {:model-id "mdl-1" :name "WRF" :version "4.3"})
    st))

(deftest rejects-unregistered-station-hard
  (let [st (fresh-store)
        request {:station-id "no-station"}
        proposal {:op :analyze-weather-data :effect :propose :confidence 0.9}
        verdict (governor/check request {} proposal st)]
    (is (:hard? verdict))
    (is (not (:ok? verdict)))
    (is (seq (:violations verdict)))
    (is (some #(= :no-station (:rule %)) (:violations verdict)))))

(deftest rejects-non-propose-effect-hard
  (let [st (fresh-store)
        request {:station-id "stn-1"}
        proposal {:op :analyze-weather-data :effect :commit :confidence 0.9}
        verdict (governor/check request {} proposal st)]
    (is (:hard? verdict))
    (is (not (:ok? verdict)))
    (is (some #(= :no-actuation (:rule %)) (:violations verdict)))))

(deftest rejects-missing-dataset-for-analyze-hard
  (let [st (fresh-store)
        request {:station-id "stn-1" :dataset-id "no-dataset"}
        proposal {:op :analyze-weather-data :effect :propose :confidence 0.9}
        verdict (governor/check request {} proposal st)]
    (is (:hard? verdict))
    (is (not (:ok? verdict)))
    (is (some #(= :no-dataset (:rule %)) (:violations verdict)))))

(deftest rejects-missing-model-for-request-model-run-hard
  (let [st (fresh-store)
        request {:station-id "stn-1" :model-id "no-model"}
        proposal {:op :request-model-run :effect :propose :confidence 0.9}
        verdict (governor/check request {} proposal st)]
    (is (:hard? verdict))
    (is (not (:ok? verdict)))
    (is (some #(= :no-model (:rule %)) (:violations verdict)))))

(deftest rejects-published-forecast-claim-hard
  (let [st (fresh-store)
        request {:station-id "stn-1"}
        proposal {:op :draft-forecast :effect :propose :confidence 0.9 :published? true}
        verdict (governor/check request {} proposal st)]
    (is (:hard? verdict))
    (is (not (:ok? verdict)))
    (is (some #(= :no-published-forecasts (:rule %)) (:violations verdict)))))

(deftest rejects-auto-issued-warning-hard
  (let [st (fresh-store)
        request {:station-id "stn-1"}
        proposal {:op :flag-severe-weather-risk :effect :propose :confidence 0.95 :auto-issue? true}
        verdict (governor/check request {} proposal st)]
    (is (:hard? verdict))
    (is (not (:ok? verdict)))
    (is (some #(= :no-direct-warnings (:rule %)) (:violations verdict)))))

(deftest escalates-flag-severe-weather-risk
  (let [st (fresh-store)
        request {:station-id "stn-1"}
        proposal {:op :flag-severe-weather-risk :effect :propose :confidence 0.95}
        verdict (governor/check request {} proposal st)]
    (is (not (:hard? verdict)))
    (is (:escalate? verdict))
    (is (not (:ok? verdict)))))

(deftest escalates-draft-forecast-with-hazardous-conditions
  (let [st (fresh-store)
        request {:station-id "stn-1"}
        proposal {:op :draft-forecast :effect :propose :confidence 0.9 :hazard? true}
        verdict (governor/check request {} proposal st)]
    (is (not (:hard? verdict)))
    (is (:escalate? verdict))
    (is (not (:ok? verdict)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        request {:station-id "stn-1" :dataset-id "ds-1"}
        proposal {:op :analyze-weather-data :effect :propose :confidence 0.4}
        verdict (governor/check request {} proposal st)]
    (is (not (:hard? verdict)))
    (is (:escalate? verdict))
    (is (not (:ok? verdict)))))

(deftest approves-clean-low-stake-analysis
  (let [st (fresh-store)
        request {:station-id "stn-1" :dataset-id "ds-1"}
        proposal {:op :analyze-weather-data :effect :propose :confidence 0.95 :stake :low}
        verdict (governor/check request {} proposal st)]
    (is (not (:hard? verdict)))
    (is (not (:escalate? verdict)))
    (is (:ok? verdict))))

(deftest approves-draft-forecast-without-hazard
  (let [st (fresh-store)
        request {:station-id "stn-1"}
        proposal {:op :draft-forecast :effect :propose :confidence 0.85 :hazard? false}
        verdict (governor/check request {} proposal st)]
    (is (not (:hard? verdict)))
    (is (not (:escalate? verdict)))
    (is (:ok? verdict))))

(deftest approves-calibration-procedure
  (let [st (fresh-store)
        request {:station-id "stn-1"}
        proposal {:op :calibrate-instrument :effect :propose :confidence 0.9 :stake :medium}
        verdict (governor/check request {} proposal st)]
    (is (not (:hard? verdict)))
    (is (not (:escalate? verdict)))
    (is (:ok? verdict))))

(ns meteorology.governor
  "MeteorologicalGovernor — the independent safety/traceability layer for
  the ISCO-08 2112 meteorology support actor (meteorologists).
  Wired as its own `:govern` node in `meteorology.actor`'s StateGraph,
  downstream of `:advise` — the Advisor has no notion of station
  provenance or safety risk, so this MUST be a separate system able to
  reject a proposal (itonami actor pattern, per ADR-2607011000 / CLAUDE.md
  Actors section).

  `check` is a pure function of (request, context, proposal, store) ->
  verdict; it never mutates the store. The StateGraph's `:decide` node
  routes on the verdict:
    :hard? true                → :hold  (irreversible, no write)
    :escalate? true            → :request-approval (interrupt-before)
    otherwise                  → :commit

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. station provenance  — the request's station must be registered.
    2. dataset verification — :analyze-weather-data ops must reference a
                              registered dataset.
    3. model verification  — :request-model-run ops must reference a
                             registered model.
    4. no-actuation — proposal :effect must be :propose.
    5. no-published-forecasts — :draft-forecast proposals can NEVER claim
                                to be final/published (draft is ONLY for
                                human review, not public issue). A forecast
                                issued directly as final/published (rather
                                than reviewed draft) is a hard, permanent block.
    6. no-direct-warnings — :flag-severe-weather-risk can only be flagged
                            for human review, never auto-issued as an actual
                            public warning (this is a public-safety-critical
                            invariant — the human release gate is absolute).

  ESCALATION invariants (:escalate? true, ALWAYS human sign-off):
    7. :flag-severe-weather-risk — always escalates (public-safety safeguard,
                                   never silently dismissed).
    8. :draft-forecast involving hazardous conditions — severe/hazardous
                                   forecasts require human review before release.
    9. low confidence (< `confidence-floor`)."
  (:require [meteorology.store :as store]))

(def confidence-floor 0.6)
(def ^:private escalating-ops #{:flag-severe-weather-risk :draft-forecast})

(defn- hard-violations [{:keys [proposal request]} station-record dataset-record model-record]
  (cond-> []
    (nil? station-record)
    (conj {:rule :no-station :detail "未登録 station"})

    (and (= :analyze-weather-data (:op proposal))
         (nil? dataset-record))
    (conj {:rule :no-dataset :detail "analyze-weather-data 前に dataset は要登録"})

    (and (= :request-model-run (:op proposal))
         (nil? model-record))
    (conj {:rule :no-model :detail "request-model-run 前に model は要登録"})

    (not= :propose (:effect proposal))
    (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

    (and (= :draft-forecast (:op proposal))
         (:published? proposal))
    (conj {:rule :no-published-forecasts
           :detail "forecast 最終化は draft 提案では不可（draft は査読用のみ、直接発行禁止）"})

    (and (= :flag-severe-weather-risk (:op proposal))
         (:auto-issue? proposal))
    (conj {:rule :no-direct-warnings
           :detail "flag-severe-weather-risk は human 査読用のみ（自動発行禁止、public safety 境界）"})))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `meteorology.store/Store`. Returns
  `{:ok? bool :violations [...] :confidence n :hard? bool :escalate? bool}`."
  [request context proposal store]
  (let [station-record (store/station store (:station-id request))
        dataset-record (when (:dataset-id request) (store/dataset store (:dataset-id request)))
        model-record (when (:model-id request) (store/model store (:model-id request)))
        hard (hard-violations {:proposal proposal :request request} station-record dataset-record model-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        is-flag? (= :flag-severe-weather-risk (:op proposal))
        is-forecast-hazard? (and (= :draft-forecast (:op proposal)) (:hazard? proposal))
        risky-op? (and (contains? escalating-ops (:op proposal))
                       (or is-flag? is-forecast-hazard?))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))

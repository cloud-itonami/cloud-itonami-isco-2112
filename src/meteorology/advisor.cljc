(ns meteorology.advisor
  "MeteorologicalAdvisor — proposes a weather operation (analyze observational data,
  draft a forecast, flag a severe-weather risk, request a model run, calibrate an
  instrument) for a registered weather station. The advisor is swappable: `mock-advisor`
  (deterministic, default in dev/tests/CI) or `llm-advisor` (wraps a real
  `langchain.model/ChatModel`). Either way the advisor ONLY produces a PROPOSAL — it
  never writes to the store and has no notion of station provenance or safety risk;
  `meteorology.governor` is the independent system that decides whether the proposal may
  proceed, per the itonami actor pattern.

  A proposal is a map:
    {:op :analyze-weather-data|:draft-forecast|:flag-severe-weather-risk
        |:request-model-run|:calibrate-instrument
     :effect :propose        ; the advisor NEVER emits a raw store write
     :stake :low|:medium|:high
     :hazard? boolean         ; true if the operation involves severe/hazardous conditions
     :confidence 0.0-1.0
     :rationale str}
  LLM parse failures always yield `:confidence 0.0` (never fabricate confidence),
  which forces the governor to escalate/hold."
  (:require [clojure.edn :as edn]
            [kotoba.lang.text :as str]))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer
  "Deterministic mock inference: reads the request's declared op/stake
  straight through (a stand-in for what an LLM would extract from free
  text), with a stake-derived confidence."
  [_store {:keys [op stake hazard?] :as request}]
  {:op op
   :effect :propose
   :stake (or stake :low)
   :hazard? (boolean hazard?)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for station " (:station-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a meteorological advisor for weather forecasting and research.
   Given a weather operation request, propose an :op, an honest :confidence
   (0.0-1.0), a :stake (:low/:medium/:high), and whether it involves
   hazardous conditions (:hazard?). Never fabricate confidence you don't have.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high :hazard? false
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high :hazard? false
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  "Wraps a `langchain.model/ChatModel`. `gen-opts` is passed through to
  `model/-generate`. Kept decoupled from any concrete model so this ns
  has no hard dependency beyond `langchain.model`'s protocol."
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "weather operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))

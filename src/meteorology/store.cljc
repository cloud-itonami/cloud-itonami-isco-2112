(ns meteorology.store
  "SSoT for the ISCO-08 2112 meteorology support actor (meteorologists).
  Store is a protocol injected into the `meteorology.actor` StateGraph — `MemStore`
  is the default, deterministic, zero-dep backend; a Datomic/kotoba-server-backed
  implementation can be swapped in without touching the actor or governor (itonami
  actor pattern, per ADR-2607011000 / CLAUDE.md Actors section).

  Domain:

    station  — a registered weather station/observatory (:station-id, :name, :location)
    dataset  — a recorded weather observational dataset associated with a station
               (:dataset-id, :station-id, :time-range, :description)
    model    — a computational weather model resource (:model-id, :name, :version)
    record   — a committed meteorological operation under a station
               (analysis result, forecast draft, severe-weather flag,
               model run request, instrument calibration) — written
               ONLY via commit-record!, never mutated in place
    ledger   — an append-only audit trail of every proposal/verdict/
               disposition, regardless of outcome (commit or hold)")

(defprotocol Store
  (station [s station-id])
  (dataset [s dataset-id])
  (model [s model-id])
  (records-of [s station-id])
  (ledger [s])
  (register-station! [s station])
  (register-dataset! [s dataset])
  (register-model! [s model])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (station [_ station-id] (get-in @a [:stations station-id]))
  (dataset [_ dataset-id] (get-in @a [:datasets dataset-id]))
  (model [_ model-id] (get-in @a [:models model-id]))
  (records-of [_ station-id] (filter #(= station-id (:station-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-station! [s station]
    (swap! a assoc-in [:stations (:station-id station)] station) s)
  (register-dataset! [s dataset]
    (swap! a assoc-in [:datasets (:dataset-id dataset)] dataset) s)
  (register-model! [s model]
    (swap! a assoc-in [:models (:model-id model)] model) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:stations {} :datasets {} :models {} :records [] :ledger []} seed)))))

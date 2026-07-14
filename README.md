# cloud-itonami-isco-2112

**ISCO-08 2112: Meteorologists** — A research-support actor for weather forecasting and meteorological operations.

## Overview

`cloud-itonami-isco-2112` is an autonomous "actor" (LLM/advisor behind an independent Governor, langgraph-clj StateGraph, append-only audit ledger) that codifies ISCO-08 unit-group 2112 (Meteorologists) as a safe, governed system for weather research operations.

The actor proposes and governs meteorological operations:

- **Analyze Weather Data**: pipeline analysis of recorded observational datasets
- **Draft Forecast**: prepare a weather forecast for human meteorologist review
- **Flag Severe Weather Risk**: surface hazardous patterns for urgent human review (always escalates)
- **Request Model Run**: propose a computational weather-model execution
- **Calibrate Instrument**: propose a sensor/station calibration procedure

## Hard Invariants (ALWAYS :hold, never overridable)

1. **Station Provenance** — the request's weather station must be registered
2. **Dataset Verification** — analyze operations must reference a registered dataset
3. **Model Verification** — model-run operations must reference a registered model
4. **No Actuation** — proposals must have `:effect :propose` only (no direct writes)
5. **No Published Forecasts** — draft forecasts can never claim to be final/published (draft is for review only)
6. **No Direct Warnings** — severe-weather flags are review-only; auto-issuing as public warnings is blocked (public-safety-critical invariant)

## Escalation Rules (ALWAYS human sign-off)

- **Severe Weather Flags** — always escalate (public-safety safeguard, never silently dismissed)
- **Hazardous Forecasts** — draft forecasts involving severe/hazardous conditions require human review
- **Low Confidence** — proposals below the confidence floor force escalation

## Architecture

```
request
  ↓
:intake → :advise → :govern → :decide ─┬→ :commit           (:ok? true)
                                        ├→ :request-approval  (:escalate? true, interrupt-before)
                                        └→ :hold              (:hard? true)
```

- **Advisor** (`meteorology.advisor`): proposes operations (mock or LLM-backed)
- **Governor** (`meteorology.governor`): enforces invariants and escalation rules
- **Actor** (`meteorology.actor`): langgraph-clj StateGraph orchestrating the flow
- **Store** (`meteorology.store`): SSoT for stations, datasets, models, records, and audit ledger

## Development

```bash
# Run tests
clj -M:test

# REPL
clj

# Build
clj -M:package  # (if defined)
```

## License

AGPL-3.0-or-later. See [LICENSE](./LICENSE).

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md).

## Governance

See [GOVERNANCE.md](./GOVERNANCE.md).

## Security

See [SECURITY.md](./SECURITY.md).

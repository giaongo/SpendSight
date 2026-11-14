# SpendSight contributor guide

SpendSight is a Junction 2026 prototype with a Kotlin Multiplatform mobile client, a Python FastAPI service, and a static HTML design reference. Keep those three roles separate.

## Instruction scope

- This file applies to the whole repository. Also read the nearest nested `AGENTS.md` before changing files under `frontend/` or `backend/`.
- If a design document conflicts with this file, this file wins. In particular, ignore the Java-service and React/web-view suggestions in `design/my-money/STRUCTURE.md`.
- Preserve unrelated work in the tree. Make the smallest coherent change and do not replace an existing implementation just to use a different stack.

## Repository map

- `frontend/`: Kotlin Multiplatform client. Shared Compose UI and client logic live in `frontend/shared/src/commonMain`.
- `frontend/androidApp/` and `frontend/iosApp/`: thin platform hosts for the shared client.
- `backend/`: Python 3.11+ FastAPI service, ledger calculations, Junction-data access, and tests.
- `design/my-money/`: visual and behavioral reference only. `index.html` is not a production client or service.
- `BACKLOG.md`: product stories and the oracle figures that must remain exact.
- `backend/API.md` and `frontend/APP.md`: runtime and integration documentation.

## Non-negotiable architecture

- Build client screens with Kotlin Multiplatform and Compose in `frontend/`. Do not add a React app, web view, separate SwiftUI screen implementation, or Android-only Java UI. Platform-specific code should be limited to hosting or capabilities that cannot live in `commonMain`.
- Implement APIs, persistence, data access, and ledger math in Python under `backend/`. Do not add a Java or Kotlin service.
- The backend owns every financial calculation. The client renders returned cents and display strings; it must not independently recompute balances, Bonus, safe-to-spend, runway, or loan figures.
- Represent money as integer cents in `*_cents` fields. Keep display formatting consistent with `backend/app/money.py`, including the euro sign and Unicode minus.
- Keep API DTOs in `frontend/shared/src/commonMain/kotlin/fi/spendsight/ApiDto.kt` aligned with backend response shapes. When a contract changes, update the implementation, client DTO/use sites, tests, and relevant docs together.

## Keep the two data worlds separate

The backend intentionally serves two unrelated datasets. Never blend their identities or figures.

1. The design sample is Aino Korhonen's hard-coded ledger in `backend/app/ledger.py`. The coach, `/tools/*`, payments, charts, Bonus, purchase, watch, effort, and loan-sketch routes use this sample. Its demo date and figures are fixed by the design, not the current system date.
2. The Junction dataset is loaded by `backend/app/junction_db.py` from the gitignored `Junction-2026-S-Pankki/` CSVs (or `JUNCTION_DIR`) and cached in `backend/.cache/junction.sqlite`. Customer Today, Spending, and Save routes use this data.

The coach does not read Junction customer data, and customer screens must not present Aino's sample figures as the selected customer's data. Preserve the “Design sample” disclosure on sample-backed screens.

## Product and safety invariants

- A request-body customer id does not choose identity; the server-side session does.
- Refused coach answers must not read or expose ledger amounts. Allowed answers run exactly one named tool, and generated wording must not alter its figure.
- Do not invent missing peer benchmarks, historical series, usage signals, lending decisions, or customer data.
- The coach cannot move money, open accounts, block cards, approve loans, or call transfer paths. Preview and confirmation flows must remain explicit and separate from coach answers.
- Refiling a payment may change its category/basket calculations but must not change whether the merchant earns Bonus.

## Design and correctness sources

- Match layout, copy, and interaction intent from `design/my-money/index.html` and `design/my-money/NOTES.md`, while implementing them in the required stack above.
- For sample ledger figures, the oracle is `selfCheck` in `design/my-money/index.html`, mirrored in `BACKLOG.md` and `backend/tests/test_oracle.py`. Do not “approximately” reproduce these values.
- Reuse design tokens and fixed copy from `frontend/shared/src/commonMain/kotlin/fi/spendsight/Theme.kt` and `UiCopy.kt` rather than scattering near-duplicates.
- Preserve API-provided `reads` paths from `GET /session`; the app should not construct customer endpoints from an assumed id.

## Verification

Run checks for the area changed. If a check cannot run because local certificates, the Junction CSVs, an emulator, or Xcode are unavailable, state that clearly in the handoff.

Backend, from `backend/`:

```bash
uv sync --extra dev
uv run pytest
```

If the existing local virtual environment is already provisioned, `.venv/bin/pytest` is also valid. The tests instantiate the real `JunctionLibrary`, so the Junction dataset must be available at its default location or through `JUNCTION_DIR`.

Frontend, from `frontend/` with JDK 17:

```bash
./gradlew :shared:allTests
./gradlew :androidApp:assembleDebug
```

For iOS-facing changes, also build the `iosApp` scheme in Xcode when that environment is available. Do not claim runtime UI verification unless the relevant app was actually launched.

## Change discipline

- Add or update focused tests for behavior changes, especially ledger math, route validation, DTO serialization, and store state transitions.
- Keep generated artifacts, local certificates, CSV datasets, SQLite caches, virtual environments, and build output out of version control.
- Update `backend/API.md` for API/runtime changes and `frontend/APP.md` for client behavior, endpoint usage, or setup changes.
- Comments and documentation should explain enduring constraints, not narrate an implementation step or temporary workaround.

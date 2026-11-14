# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

SpendSight is a Junction 2026 hackathon project: a money coach for an S-Pankki customer. It has a Kotlin Multiplatform phone app (`frontend/`), a Python FastAPI service (`backend/`), and a static HTML design (`design/my-money/`).

## Stack rules (from AGENTS.md — these override the design docs)

- The client is Kotlin Multiplatform only. Shared UI and logic go in `frontend/shared/src/commonMain`. Do not add React, a web view, SwiftUI screens, or an Android-only Java UI. `androidApp/` and `iosApp/` only host the shared Compose UI.
- The service is Python only. Do not write the API or ledger math in Java or Kotlin.
- `design/my-money/index.html` is the screen design to match, not the app. Where `design/my-money/STRUCTURE.md` mentions a Java service or React web views, ignore that and follow the rules above.
- The service owns every money figure. The app renders the cents (and display strings) the service returns. Do not recompute ledger math in the client.

## Commands

Backend (run from `backend/`; the venv lives at `backend/.venv`, and there is also a `uv.lock`):

```bash
pip install -e '.[dev]'            # or: uv sync --extra dev
.venv/bin/pytest                   # all tests
.venv/bin/pytest tests/test_oracle.py::test_name   # a single test
.venv/bin/python -m app            # runs uvicorn over HTTPS on 127.0.0.1:8000
```

`python -m app` (`app/__main__.py`) needs `backend/.cache/certs/localhost.crt` and `localhost.key`. Both are gitignored and no script in the repo creates them. The long form is in `backend/API.md`. Interactive docs are at `https://127.0.0.1:8000/docs`.

Frontend (run from `frontend/`; needs JDK 17):

```bash
./gradlew :androidApp:installDebug     # build and install on an emulator or device
./gradlew :shared:allTests             # shared tests (commonTest)
```

`/run-android` and `/run-ios` install and launch the app. The steps are in `.agents/skills/run-android/SKILL.md` and `.agents/skills/run-ios/SKILL.md`. Android Studio’s Run button cannot launch the iOS app on Xcode 15.0.1.

The app calls `https://10.0.2.2:8000` from the Android emulator and `https://127.0.0.1:8000` on iOS (`defaultBaseUrl()` in each platform's `Http.kt`). It trusts the self-signed dev cert only for loopback, `10.0.2.2`, and private LAN addresses.

## Architecture

The backend serves **two separate data worlds**. Keep them apart.

1. **Design sample (Aino Korhonen).** `app/ledger.py` holds a hard-coded sample ledger: October rolled up by place, November payments 1–13, holds, balance, salary, Bonus steps, and loan arithmetic. "Today" in the demo is 13 Nov. `app/coach.py` runs `POST /coach/ask`: it classifies the question, refuses (with no ledger amounts) or runs exactly one named tool, and fills a fixed card (`refused`, `disclaimer`, `source`, `fact{kicker,figure,because}`, `next`, `screen`). `app/store.py` `Session` is the one signed-in demo session, holding the ledger, the thread, the audit (intent, tool, time, account; never the question text), and the effort setting. The `/tools/*`, `/payments`, `/ledger/summary`, `/charts`, `/bonus`, `/purchase`, `/watch`, `/settings/effort`, and `/loan/sketch` routes all read this sample.
2. **Junction dataset (real CSVs).** `app/junction_db.py` `JunctionLibrary` loads `Junction-2026-S-pankki/` at startup (override with `JUNCTION_DIR`). That folder is gitignored. The 3.6M-row transaction CSV is indexed once into `backend/.cache/junction.sqlite` and reopened when the index is newer than the CSV. A Git LFS pointer file is rejected. `app/customer_data.py` `CustomerBook` serves `/customers/{id}/today|spending|save|months`. `/today`, `/spending`, and `/save` with no id use CUST00001.

The coach never reads the Junction data, and Aino's figures are never computed from it.

Other backend conventions:
- Money is integer cents in `*_cents` fields. Display strings use `€` and a unicode minus (`app/money.py`). `js_round` copies JavaScript rounding so figures match the design.
- A customer id in a request body is ignored. The session decides who the customer is.
- `app/main.py` `create_app()` builds the app. Tests use it through the `client` fixture in `tests/conftest.py`, so tests load the real Junction data.

Frontend flow: `ApiClient.kt` (Ktor; OkHttp on Android, Darwin on iOS) → `ApiDto.kt` (serializable DTOs that mirror the service responses) → `Store.kt` `SpendSightStore` (state, loading, actions) → `App.kt` / `Screens.kt` (Compose UI, four tabs: Today, Spending, Save, SpendSight). `Theme.kt` and `UiCopy.kt` hold the design tokens and fixed copy. The opening load makes about 18 calls in parallel, and one failure fails the whole load (see `frontend/APP.md`). Today, Spending, and Save follow the `reads` paths returned by `GET /session`. Design-sample screens show a "Design sample" banner.

## Reference docs

- `BACKLOG.md`: stories (APP-xx, API-xx) and the **oracle table** of figures that must match exactly.
- `backend/tests/test_oracle.py`: those oracle figures as tests. The source of truth is `selfCheck` in `design/my-money/index.html`. A change to ledger math must keep these passing.
- `backend/API.md`: routes and the Junction data. `frontend/APP.md`: how the app runs, its addresses, and which screen calls which route.
- `design/my-money/NOTES.md`: the full worked derivation of every sample figure, such as safe-to-spend, slip lines, Bonus steps, and runway.

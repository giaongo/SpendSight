# SpendSight app

Kotlin Multiplatform client in `frontend/`. The screens and the HTTP client live in `frontend/shared` (`commonMain`). Android and iOS only host that shared UI.

The service is documented in [`backend/API.md`](../backend/API.md). The interactive reference is [https://127.0.0.1:8000/docs](https://127.0.0.1:8000/docs). That page is the reference. The app does not open it.

## Run

Start the service first, from `backend/`, as `backend/API.md` describes. It listens on `https://127.0.0.1:8000` with the certificate in `backend/.cache/certs/`.

Then, from `frontend/`:

```bash
./gradlew :androidApp:installDebug
```

That installs the debug app on a connected emulator or phone. The package is `fi.spendsight`. The build needs JDK 17. `minSdk` is 26.

For iOS, open `frontend/iosApp/iosApp.xcodeproj` and run the `iosApp` scheme. The deployment target is 15.2. Xcode builds the shared framework with Gradle. The simulator uses this computer’s `127.0.0.1`.

## Address

`baseUrl` is the service address. Each platform sets the default in `defaultBaseUrl()`:

| Where the app runs | Default | File |
| --- | --- | --- |
| Android emulator | `https://10.0.2.2:8000` | `shared/src/androidMain/kotlin/fi/spendsight/Http.kt` |
| This computer or the iOS simulator | `https://127.0.0.1:8000` | `shared/src/iosMain/kotlin/fi/spendsight/Http.kt` |

`127.0.0.1` on the Android emulator is the emulator, so a build that calls `https://127.0.0.1:8000` shows “Failed to connect to /127.0.0.1:8000”. `10.0.2.2` is this computer from that emulator.

The store reads `baseUrl` when it is created. Change the address, then rebuild. A physical phone needs this computer’s LAN address, and the service has to listen on that address. The process in `backend/API.md` binds `127.0.0.1` only.

The client trusts the dev certificate for a local address: loopback, `10.0.2.2`, and private LAN ranges. Any other host uses the normal trust store.

## Startup

The first screen stays on “The service did not answer” until every call in the opening load succeeds. One failed call fails the whole load. The error line is the failure, and the line under the button is the `baseUrl` this build used. Try again runs the same load.

That load calls, in parallel:

| Call | What the phone keeps |
| --- | --- |
| `GET /session` | Customer id, postal code, age, account, balance, the coach’s opening note, and the `reads` paths for this customer |
| `reads.today` | Today. The session names this path, for example `/customers/CUST00001/today` |
| `reads.spending` | Latest month on Spending |
| `reads.save?years=5` | Save, five-year window |
| `GET /settings/effort` | Design-sample effort rows |
| `GET /payments` | Design-sample payments |
| `GET /ledger/summary` | Design-sample holds and what is left after salary |
| `GET /tools/week` | This week |
| `GET /tools/spender` | Spender |
| `GET /tools/dining` | Dining out |
| `GET /tools/impulse` | Impulse |
| `GET /tools/runway` | Days left |
| `GET /tools/subscriptions` | Subscriptions |
| `GET /charts` | Charts |
| `GET /bonus` | Bonus |
| `GET /purchase` | This purchase |
| `GET /watch` | Watch |
| `GET /loan/sketch?price_eur=220000&rate_percent=4.0&years=25&down_percent=10` | Loan sketch |

`/docs` and `/database` are not called. Today, Spending, and Save use the paths in `reads` from `GET /session`, so a different customer on the service is the customer the phone loads. Month and year changes stay on those same paths.

## Tabs

| Tab | What it shows |
| --- | --- |
| Today | The session’s `reads.today`: balance, as-of date, latest salary, money in and out for the latest month, transaction count |
| Spending | The session’s `reads.spending`. Earlier and Later add `?month=YYYY-MM`. A month the customer does not have stays on the previous month and shows the 400 detail |
| Save | The session’s `reads.save` with `?years=`. The chips are 1, 3, and 5. This is recorded deposits. It does not move money |
| SpendSight | Coach. The opening bubble is the `note` from `GET /session` |

A category that exists on the loaded spending month lists those transactions. The amounts are the strings the service sent. Those rows have no filing sheet.

The account rows on Save are copy stored in the app. They explain where a euro can sit. They are not a second ledger.

## Design sample

Any screen that starts with “Design sample. Today, Spending, and Save are this customer’s transactions. This screen is not.” is the design sample from the service, not CUST00001’s file.

| Screen | Call |
| --- | --- |
| This week | `GET /tools/week`, plus holds from `GET /ledger/summary` |
| Spender | `GET /tools/spender` |
| Dining out | `GET /tools/dining` |
| Baskets, Charts | `GET /payments`, `GET /charts` |
| Impulse | `GET /tools/impulse` |
| Days left | `GET /tools/runway` |
| Subscriptions | `GET /tools/subscriptions` |
| Bonus | `GET /bonus` |
| This purchase | `GET /purchase` |
| Watch | `GET /watch` |
| Home | Sample buffer and October Bonus. The service has no ASP plan for this customer, so the screen does not invent one |
| Loan sketch | `GET /loan/sketch` |

The coach sends `POST /coach/ask` with `{ "text": "..." }`, at most 280 characters. The card is the design sample. A chip is one of those questions. Privacy → “Delete this thread” sends `DELETE /thread` and restores the opening note.

The effort picker sends `PUT /settings/effort` with `{ "effort": "..." }`. Refiling a design-sample payment sends `POST /payments/{id}/filing` with `{ "category": "..." }`. Customer transactions are not refiled.

The loan slider sends `price_eur` and `rate_percent`, with `years` 25 and `down_percent` 10. The price snaps to €10,000 steps from €150,000 to €400,000. The rate snaps to 0.5% steps from 2.0 to 6.0. A reply for an older price or rate is ignored.

The phone does not transfer money, book a meeting, cancel a subscription, or mark a watch item. Those controls stay on the device.

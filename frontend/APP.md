# SpendSight app

Kotlin Multiplatform client in `frontend/`. The screens and the HTTP client live in `frontend/shared` (`commonMain`). Android and iOS only host that shared UI.

The service is documented in [`backend/API.md`](../backend/API.md). The interactive reference is [https://127.0.0.1:8000/docs](https://127.0.0.1:8000/docs). That page is the reference. The app does not open it.

## Launcher icon

[`branding/spendsight-app-icon.svg`](branding/spendsight-app-icon.svg) is the editable launcher-icon source: a chat bubble with a magnifying lens over spending bars, on a green gradient tile.

- Android uses adaptive vector resources in `androidApp/src/main/res/drawable/`: `ic_launcher_background` (gradient), `ic_launcher_foreground` (the artwork in the SVG's own 1024-unit coordinates, mapped onto the 72dp visible area), and `ic_launcher_monochrome` (a line version for the Android 13 themed icon). VectorDrawable has no blur, so stacked translucent offsets approximate the bubble's drop shadow. If the SVG changes, update these by hand.
- iOS uses the opaque PNG sizes in `Assets.xcassets/AppIcon.appiconset`, because iOS launcher assets do not accept SVG files. They are rendered from a full-bleed copy of the SVG (the tile's `rx="228"` removed, because iOS applies its own corner mask), then downsampled to each size in `Contents.json`.

## Run

Start the service first, from `backend/`, as `backend/API.md` describes. It listens on `https://127.0.0.1:8000` with the certificate in `backend/.cache/certs/`.

`/run-backend` starts that service. The steps are in [`.agents/skills/run-backend/SKILL.md`](../.agents/skills/run-backend/SKILL.md).

`/run-android` installs the debug app and opens it. The steps are in [`.agents/skills/run-android/SKILL.md`](../.agents/skills/run-android/SKILL.md). From `frontend/`, the same install is:

```bash
./gradlew :androidApp:installDebug
```

The package is `fi.spendsight`. The launcher is `fi.spendsight.MainActivity`. The build needs JDK 17. `minSdk` is 26.

`/run-ios` builds the simulator app and opens it. The steps are in [`.agents/skills/run-ios/SKILL.md`](../.agents/skills/run-ios/SKILL.md). Android Studio’s Run button cannot launch this project on Xcode 15.0.1. The simulator uses this computer’s `127.0.0.1`.

## Address

`baseUrl` is the service address. Each platform sets the default in `defaultBaseUrl()`:

| Where the app runs | Default | File |
| --- | --- | --- |
| Android emulator | `https://10.0.2.2:8000` | `shared/src/androidMain/kotlin/fi/spendsight/Http.kt` |
| This computer or the iOS simulator | `https://127.0.0.1:8000` | `shared/src/iosMain/kotlin/fi/spendsight/Http.kt` |

`127.0.0.1` on the Android emulator is the emulator, so a build that calls `https://127.0.0.1:8000` shows “Failed to connect to /127.0.0.1:8000”. `10.0.2.2` is this computer from that emulator.

### Deployed service (OPS-10)

Set `spendsight.apiUrl` to build an app that calls the deployed service instead. It must be `https://host` with no path or trailing slash.

| How | Example |
| --- | --- |
| Gradle flag | `./gradlew :androidApp:installDebug -Pspendsight.apiUrl=https://api.95-133-254-29.sslip.io` |
| Environment | `SPENDSIGHT_API_URL=https://api.95-133-254-29.sslip.io ./gradlew :androidApp:installDebug` |
| `frontend/local.properties` (gitignored) | `spendsight.apiUrl=https://api.95-133-254-29.sslip.io` |

Use `local.properties` for iOS: Xcode runs Gradle itself and does not pass your flags. Remove the line, or leave it empty, to go back to the local address. Gradle writes the value into a generated `DEPLOYED_BASE_URL` constant, so change it and rebuild. The deployed service has a Let's Encrypt certificate from Caddy, so it needs no special trust. The dev-certificate exception still covers local addresses only.

The store reads `baseUrl` when it is created. Change the address, then rebuild. A physical phone needs this computer’s LAN address, and the service has to listen on that address. The process in `backend/API.md` binds `127.0.0.1` only.

The client trusts the dev certificate for a local address: loopback, `10.0.2.2`, and private LAN ranges. Any other host uses the normal trust store.

## Startup

The first screen stays on “The service did not answer” until every call in the opening load succeeds. One failed call fails the whole load. The error line is the failure, and the line under the button is the `baseUrl` this build used. Try again runs the same load.

That load calls, in parallel:

| Call | What the phone keeps |
| --- | --- |
| `GET /customers/CUST00007` | Customer id, postal code, age, account, balance, and the `reads` paths for this customer (`CUSTOMER_ID` in `ApiClient.kt`) |
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

Today, Spending, and Save use the paths in `reads` from `GET /customers/CUST00007`, so a different customer on the service is the customer the phone loads. Month and year changes stay on those same paths. Offers are not part of this opening load. The Offers tab calls `GET /customers/{customer_id}/coupons` and `GET /campaigns`. Activate calls the `activate` path on that coupon. SpendSight also reads the previous month afterwards, and ignores that call when the customer has no earlier month.

## Tabs

| Tab | What it shows |
| --- | --- |
| Home | The session’s `reads.today`: balance and as-of date. Hide is local. See transactions opens `reads.spending` |
| SpendSight | The loaded spending month. Essentials and everyday are sums of outflow cents the service already returned. Salary and invest deposits are left out. “Worth a look” compares the previous month when that read succeeds. “What can I safely spend this week?” is a question to the coach. The screen does not invent that figure |
| Savings | The session’s `reads.save` with `?years=`. The chips are 1, 3, and 5. The hero is `deposited_cents`. Account rows are explanations, not balances |
| Offers | `GET /customers/{customer_id}/coupons` and `GET /campaigns`. The benefit line is the service’s `benefit`. Activate is `POST` to the coupon’s `activate` path |

The four roots have no white bar. A drill-in screen has a short bar. Language is a menu on that bar and on each green header, in the order Finnish (`FI`), English (`EN`), Swedish (`SW`). English is the language the app opens in. The choice stays on this phone and is not saved. About this account is in the same menu. Those words live in `strings.xml` (`translations/values`, `translations/values-fi-rFI`, and `translations/values-sv-rFI`) and screens read them with `t()`. Known category names, including Housing, are shown in the selected language. A filing request still sends the English name. The fixed notes on Today, Spending, and Save are translated. Any other sentence the service writes stays as sent. Amounts use the design’s euro format in every language: `1 028,30 €` and `−5,60 €`. Amounts the service writes inside a sentence, such as a coach answer, are shown in the same format. A chip asks in the language that is selected.

A category that exists on the loaded spending month lists those transactions. The rows show the service’s cents in the selected number format. Those rows have no filing sheet.

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

The chat sends `POST /customers/{customer_id}/coach/ask` with `{ "text": "..." }`, at most 280 characters. The customer id is `CUSTOMER_ID` (CUST00007). The reply is `answer`, `lead`, `detail`, `card`, `caption`, `follow_ups`, `model`, `source`, and `disclaimer`. The five questions above the field each draw their own card from that payload. Any other reply that includes a card uses one shared card. The call can take up to a minute. Privacy → “Delete this thread” clears the screen. The service does not store the question.

The effort picker sends `PUT /settings/effort` with `{ "effort": "..." }`. Refiling a design-sample payment sends `POST /payments/{id}/filing` with `{ "category": "..." }`. Customer transactions are not refiled.

The loan slider sends `price_eur` and `rate_percent`, with `years` 25 and `down_percent` 10. The price snaps to €10,000 steps from €150,000 to €400,000. The rate snaps to 0.5% steps from 2.0 to 6.0. A reply for an older price or rate is ignored.

The phone does not transfer money, book a meeting, cancel a subscription, or mark a watch item. Those controls stay on the device.

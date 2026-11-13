# Stack

- The frontend is Kotlin Multiplatform. Client UI and shared client logic live in `frontend/`. Do not add a React app, a web view, a Swift client, or an Android-only Java UI for these screens.
- The backend is Python. APIs and ledger math live in `backend/`. Do not implement the service in Java or Kotlin.
- `design/my-money/` is the screen design to match. It is not the app and not the service. Where that design describes a Java service or a React web view, follow this file instead.

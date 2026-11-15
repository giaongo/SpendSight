---
name: qa-android-windows
description: Build, install and visually check the SpendSight app on the Android emulator from a Windows machine (Git Bash), cheaply. Use for QA of screens, or when asked to screenshot or verify the app on the emulator.
allowed-tools: Bash, Read
---

Windows version of run-android, plus a cheap QA loop. Read-only: do not edit code while checking; report findings.

## Setup (Git Bash)
- Repo: `C:/Users/thuyn/Desktop/junction/SpendSight`. Frontend in `frontend/`, backend in `backend/`.
- `export JAVA_HOME=/c/Users/thuyn/.jdks/jdk-17.0.21+9 ANDROID_HOME='C:\Users\thuyn\AppData\Local\Android\Sdk'`
- adb: `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"`. Emulator is usually `emulator-5554`. If `adb devices` lists none, stop and say so.
- Install: `cd frontend && ./gradlew :androidApp:installDebug -q 2>&1 | grep -v "^w:\|SDK processing" | tail -5`
- Backend (restart after any backend change): `cd backend && .venv/Scripts/python -m app` in the background. It is up when `curl -sk https://127.0.0.1:8000/session` returns 200. The app reaches it as `https://10.0.2.2:8000`.
- Launch: `adb shell am force-stop fi.spendsight && adb shell monkey -p fi.spendsight -c android.intent.category.LAUNCHER 1`, then wait about 8 s.

## Cheap checking
- Prefer text over images. `adb shell uiautomator dump /sdcard/ui.xml && adb exec-out cat /sdcard/ui.xml` lists every on-screen string and its bounds; grep it for the expected text first.
- Take a screenshot only to judge layout (clipping, overlap): `adb exec-out screencap -p > file.png`, then Read it. Keep it to the few that matter.
- Tap the centre of a node's bounds: `adb shell input tap X Y`. Type with `%s` for spaces: `adb shell input text "What%sif%sI%sinvest%sthat%smoney?"`. Scroll: `adb shell input swipe 540 1600 540 600 400`.
- Wait 2 to 5 s after taps that call the backend.

## Demo path and expected figures (CUST00007, "today" is Wed 30 Sep)
- Home first card: safe to spend 133,46 €, on track, of 323,85 € weekly. Breakdown: income 3 475,32 €, essentials 2 071,99 €, free 1 403,33 €, spent 190,39 €, about 26,69 € a day.
- SpendSight tab header: "Safe to spend this week", 133,46 €.
- "How could I save more?" gives 239,72 € a month for 2 fewer restaurant visits; "What if I invest that money?" gives 98 532,99 € after 20 years at an example 5 %.
- Money must show as `239,72 €` (comma, trailing euro sign), never `€239.72`.
- English UI only for the demo.

Report PASS or FAIL per step, quote exact on-screen text for anything wrong, and list screenshot paths. Keep the report under 250 words.

---
name: run-ios
description: Build the SpendSight iOS app and launch it on a booted iOS Simulator. Use when the user runs /run-ios or asks to run, install, or open the SpendSight app on iOS. Do not use the Android Studio Run button.
allowed-tools: Bash
argument-hint: "[simulator name or UDID]"
---

Build and launch the SpendSight app on the iOS Simulator.

1. Work in `/Users/sond/Desktop/Work/Hackathon/frontend/iosApp`. If the current directory is somewhere else, `cd` there first. The scheme is `iosApp`. The bundle id is `fi.spendsight`. The product name is `SpendSight`.
2. Choose a simulator. If the user passed a simulator name or UDID with the command, use that. Otherwise run `xcrun simctl list devices booted`. One booted simulator means use it. Several means stop and ask which one. None means boot the simulator named `iPhone 15`.
3. Do not install on a USB iPhone. This machine’s Xcode is 15.0.1 (iOS 17 SDK). A phone on a newer iOS is not an install target. Android Studio’s Run button cannot launch this project on that Xcode. Use `xcodebuild` and `simctl`.
4. If `xcrun simctl list runtimes` lists no iOS runtime, the simulator disk images are unmounted. Remount them with `sudo launchctl kickstart -k system/com.apple.CoreSimulator.simdiskimaged`, then list runtimes again. Stop if that command needs an approval the user has not given.
5. Build for the simulator. Unset `OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED` so the Xcode script runs Gradle and embeds the shared framework. The project already excludes `x86_64` and `i386` for the simulator, so the destination below stays on `arm64`.

```bash
unset OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED
xcodebuild \
  -project iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build/DerivedData \
  build
```

Wait until `xcodebuild` exits. The app is `build/DerivedData/Build/Products/Debug-iphonesimulator/SpendSight.app`. On failure, show the error and stop.
6. Boot the chosen simulator if it is shut down, then install and launch. `simctl launch --console-pty` blocks. Do not use it.

```bash
xcrun simctl boot "<udid>"
open -a Simulator
xcrun simctl install <udid> build/DerivedData/Build/Products/Debug-iphonesimulator/SpendSight.app
xcrun simctl terminate <udid> fi.spendsight
xcrun simctl launch <udid> fi.spendsight
```

`boot` on an already-booted simulator is fine. `terminate` on a bundle that is not running is fine. Report the simulator name and the launch pid.

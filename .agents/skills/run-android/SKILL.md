---
name: run-android
description: Install the SpendSight debug build and launch it on a connected Android device or emulator. Use when the user runs /run-android or asks to run, install, or open the SpendSight app on Android.
allowed-tools: Bash
argument-hint: "[adb serial]"
---

Install and launch the SpendSight debug app on Android.

1. Work in `/Users/sond/Desktop/Work/Hackathon/frontend`. If the current directory is somewhere else, `cd` there first.
2. The build needs JDK 17. If `java -version` is not 17, set `JAVA_HOME` to the result of `/usr/libexec/java_home -v 17`, or to `/opt/homebrew/opt/openjdk@17` when that directory exists.
3. If the user passed an adb serial with the command, target that device with `adb -s <serial>`. Otherwise run `adb devices`. One device means use it. Several devices means stop and ask which serial to use. No device means stop and say a phone or emulator must be connected and authorized.
4. Run:

```bash
./gradlew :androidApp:installDebug
adb -s <serial> shell am force-stop fi.spendsight
adb -s <serial> shell am start -n fi.spendsight/fi.spendsight.MainActivity
```

The debug application id is `fi.spendsight`. The launcher is `fi.spendsight.MainActivity`. `force-stop` drops a process that is still showing an older build.
5. Wait until Gradle exits. Report whether install and launch succeeded, including the device serial. On failure, show the error and stop.

# Water Reminder

Native Android app built with Kotlin and Jetpack Compose. Stage 2 implementation based on [the design plan](APP_PLAN.md).

## Implemented

- Today: circular progress, configurable quick-add buttons, custom drink amounts, recent entries, snackbar undo, and confirmed deletion.
- History: seven-day chart, selectable days, previous-week navigation, daily totals, average, and goal completion count.
- Settings: validated daily goal and quick-add sizes, interval selection, and Android time pickers.
- Private local persistence for entries, settings, first tracking day, and daily goal history. No network permission or account.
- Refresh after returning to the app and at local midnight while open.
- Scrollable layouts and labeled icon actions.

Reminder preferences can be edited, but reminders are explicitly marked inactive. Notification permission, scheduling, and delivery are Stage 3 work.

## Open and run

1. Install Android Studio and Android SDK Platform 35. Use JDK 17 for Gradle.
2. Open this project folder in Android Studio and allow Gradle sync.
3. Select an emulator or connected device running Android 8.0 (API 26) or later.
4. Run the `app` configuration.

Windows terminal commands, after setting JAVA_HOME to JDK 17 and ANDROID_HOME to the SDK:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk` after a successful build.

Build versions: Android Gradle Plugin 8.9.2, Gradle 8.11.1, Kotlin/Compose compiler 2.0.21, Compose BOM 2024.12.01, compile/target SDK 35. These are pinned for reproducibility rather than presented as the newest releases.

## Verification status

Verified on 20 October 2026 with JDK 17.0.20.1 and Android SDK Platform 35: `testDebugUnitTest assembleDebug` succeeded. All three unit tests passed, covering local midnight boundaries, averages, amount validation, and reminder window validation. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Emulator rendering and device behavior have **not** been verified. Non-blocking warnings concern SDK metadata compatibility, a deprecated Compose path method, and an unstripped bundled native library.

Device acceptance checks for the next verification stage:

- Log quick and custom drinks; undo and delete; restart and confirm persistence.
- Change goal and quick amounts; reject invalid inputs; verify historical goal snapshots.
- Navigate weekly history and select days, including empty days.
- Check font scaling, screen sizes, and TalkBack.
- Cross midnight and change timezone; verify totals refresh.
- Once Stage 3 lands, verify permission denial, reminder delivery, reboot restoration, and goal-based pausing.

Data is local and Android backup is disabled. Uninstalling the app removes its saved history; export and sync are outside the first version.

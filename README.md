# Water Reminder

Native Android app built with Kotlin and Jetpack Compose. Tracking and reminders based on [the design plan](APP_PLAN.md).

## Implemented

- Today: circular progress, configurable quick-add buttons, custom drink amounts, recent entries, snackbar undo, and confirmed deletion.
- History: seven-day chart, selectable days, previous-week navigation, daily totals, average, and goal completion count.
- Settings: validated daily goal and quick-add sizes, interval selection, and Android time pickers.
- Private local persistence for entries, settings, first tracking day, and daily goal history. No network permission or account.
- Refresh after returning to the app and at local midnight while open.
- Scrollable layouts and labeled icon actions.

Reminders include an enable switch, contextual notification permission request, blocked-notification settings link, and next reminder status. Inexact Android alarms respect the selected hours, restart the interval after drinking, pause at the daily goal, and restore the schedule after reboot, app updates, and clock/timezone changes. Delivery can be delayed by Android battery management. Force-stopping the app cancels alarms until it is opened again.

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

First-version review fixes (21 October 2026): reminder windows now share DST-aware boundaries; tracking start is stored as an instant for timezone-correct statistics; editor drafts survive activity recreation; tabs retain independent scroll positions; explicit backup/transfer exclusions are configured. Versioned snapshot loading validates settings and records, preserves a damaged original before repair, and shows a recovery notice. See [review and fix details](docs/testing/V1_REVIEW.md), including the date-only legacy migration limitation and outstanding device checks.

Latest validation: all 21 unit tests passed after the final changes; debug APK build and lint succeeded with zero errors and eight informational warnings (dependency updates and KTX suggestions). The updated APK has not been installed over the running manual-test emulator. The following Stage 3 results document the earlier baseline.

Verified on 20 October 2026 with JDK 17.0.20.1 and Android SDK Platform 35. Nine unit tests cover local midnight boundaries, averages, amount validation, reminder intervals, quiet hours, goal completion, overdue reminders, and daylight-saving changes. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Live Android API 37 emulator checks verified rendering, drink tracking, permission denial, notification delivery, goal-based pausing, alarm cancellation, and restoration after reboot. See the [emulator report and notification screenshot](docs/testing/EMULATOR_TEST_REPORT.md) for test methods and environment limitations.

Final Stage 3 validation: `testDebugUnitTest lintDebug assembleDebug --offline --no-daemon` succeeded. All nine tests passed, and lint reported no errors. The remaining lint warnings are informational (dependency updates, backup policy suggestions, and KTX edit suggestions).

Device acceptance checks for the next verification stage:

- Log quick and custom drinks; undo and delete; restart and confirm persistence.
- Change goal and quick amounts; reject invalid inputs; verify historical goal snapshots.
- Navigate weekly history and select days, including empty days.
- Check font scaling, screen sizes, and TalkBack.
- Cross midnight and change timezone; verify totals refresh.
- Verify permission denial, reminder delivery, reboot restoration, and goal-based pausing.

Data is local and Android backup is disabled. Uninstalling the app removes its saved history; export and sync are outside the first version.
